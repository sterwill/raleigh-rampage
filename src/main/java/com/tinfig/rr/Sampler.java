package com.tinfig.rr;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.beadsproject.beads.core.AudioContext;
import net.beadsproject.beads.core.Bead;
import net.beadsproject.beads.core.BeadArray;
import net.beadsproject.beads.core.io.JavaSoundAudioIO;
import net.beadsproject.beads.data.Sample;
import net.beadsproject.beads.ugens.Gain;
import net.beadsproject.beads.ugens.SamplePlayer;
import net.beadsproject.beads.ugens.SamplePlayer.LoopType;

import org.apache.commons.configuration.ConfigurationException;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.googlecode.javacv.CanvasFrame;
import com.tinfig.rr.processors.FlowTracker.FlowSize;

public class Sampler {
	private static final String LARGE_DAMAGE_POINTS = "sampler.largeDamagePoints";
	private static final String MEDIUM_DAMAGE_POINTS = "sampler.largeDamagePoints";
	private static final String SMALL_DAMAGE_POINTS = "sampler.largeDamagePoints";

	private static final String MILD_CHAOS_POINTS = "sampler.mildChaosPoints";
	private static final String HEAVY_CHAOS_POINTS = "sampler.heavyChaosPoints";

	private static enum Category {
		// Ambient, looped
		RECONSTRUCTION,

		// Ambient looped
		CITY,

		// Played once at start of action
		ROBOT_INTRO, LIZARD_INTRO, OTHER_INTRO,

		// Ambient, not looped; triggered in action according to general level
		// of chaos
		MILD_CHAOS, HEAVY_CHAOS,

		// Triggered on damage activities
		SMALL_DAMAGE, MEDIUM_DAMAGE, LARGE_DAMAGE,

		// Triggered after some damage
		CRUMBLE,

		// Triggered manually
		SCREAM,

		// Whatever
		CIRCUS
	}

	private static enum Phase {
		RECONSTRUCT, ACTION, STOP
	}

	private static enum Monster {
		ROBOT, LIZARD, OTHER
	}

	private static enum Volume {
		AMBIENT, INTRO, FULL, EFFECT_LOW, EFFECT_MEDIUM, EFFECT_HIGH,
	}

	private final Random random = new Random();

	private AudioContext ac;

	private FlowSize[] deviceFlow;
	private JLabel[] deviceStatusLabels;
	private CanvasFrame controlFrame;

	private int smallDamagePoints = 2;
	private int mediumDamagePoints = 5;
	private int largeDamagePoints = 10;

	private int mildChaosPoints = 500;
	private int heavyChaosPoints = 1500;

	private Monster monster = Monster.LIZARD;
	private Phase phase = Phase.STOP;
	private int score = 0;
	private boolean monsterIntro;
	private long lastChaosEffectSecond;

	private final Multimap<Category, Sample> samples = HashMultimap.create();
	private final BeadArray samplePlayers = new BeadArray();
	private final WeakHashMap<SamplePlayer, Category> beadCategories = new WeakHashMap<>();
	private JScrollPane scrollPane;
	private JPanel controlPanel;
	private JLabel scoreLabel;
	private JLabel chaosLabel;

	public Sampler() {
		samplePlayers.setForwardKillCommand(true);
	}

	public void start(File soundsDir, Settings settings, int[] devices) {
		smallDamagePoints = settings.getInteger(SMALL_DAMAGE_POINTS, smallDamagePoints);
		mediumDamagePoints = settings.getInteger(MEDIUM_DAMAGE_POINTS, mediumDamagePoints);
		largeDamagePoints = settings.getInteger(LARGE_DAMAGE_POINTS, largeDamagePoints);

		mildChaosPoints = settings.getInteger(MILD_CHAOS_POINTS, mildChaosPoints);
		heavyChaosPoints = settings.getInteger(HEAVY_CHAOS_POINTS, heavyChaosPoints);

		deviceFlow = new FlowSize[devices.length];

		JavaSoundAudioIO aio = new JavaSoundAudioIO();
		JavaSoundAudioIO.printMixerInfo();

		// Use 0 with pulseaudio with both jack sink and source
		aio.selectMixer(0);

		ac = new AudioContext(aio);

		loadSamples(soundsDir, ac);

		ac.start();

		controlFrame = new CanvasFrame("Sampler");
		controlFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

		scrollPane = new JScrollPane(controlPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setMinimumSize(new Dimension(400, 700));
		scrollPane.setPreferredSize(new Dimension(400, 700));

		controlFrame.getContentPane().add(scrollPane);

		Processor.createRadioGroup(controlPanel, new String[] { "Robot", "Lizard", "Other" }, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				switch (((JRadioButton) e.getSource()).getText()) {
				case "Robot":
					setMonster(Monster.ROBOT);
					break;
				case "Lizard":
					setMonster(Monster.LIZARD);
					break;
				case "Other":
					setMonster(Monster.OTHER);
					break;
				}
			}
		}, monster.toString(), "Monster");

		Processor.createRadioGroup(controlPanel, new String[] { "Stop", "Reconstruct", "Action" },
				new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						switch (((JRadioButton) e.getSource()).getText()) {
						case "Reconstruct":
							setPhase(Phase.RECONSTRUCT);
							break;
						case "Action":
							setPhase(Phase.ACTION);
							break;
						case "Stop":
							setPhase(Phase.STOP);
							break;
						}
					}
				}, phase.toString(), "Phase");

		Processor.createButton(controlPanel, "Monster Intro", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				handleMonsterIntro();
			}
		});
		Processor.createButton(controlPanel, "Large Damage", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (phase == Phase.ACTION) {
					play(Category.LARGE_DAMAGE, Volume.FULL, false, true);
				}
			}
		});
		Processor.createButton(controlPanel, "Scream", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (phase == Phase.ACTION) {
					play(Category.SCREAM, Volume.EFFECT_MEDIUM, false, true);
				}
			}
		});
		Processor.createButton(controlPanel, "Circus", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int confirm = JOptionPane.showOptionDialog(controlFrame, "Are you sure you want to play circus music?",
						"Circus Music?  Now?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null,
						null);
				if (confirm == JOptionPane.YES_OPTION) {
					play(Category.CIRCUS, Volume.EFFECT_LOW, false, true);
				}
			}
		});

		scoreLabel = Processor.createStatusPanel(controlPanel, "Score: ", "0");
		chaosLabel = Processor.createStatusPanel(controlPanel, "Chaos Level: ", "peaceful");

		deviceStatusLabels = new JLabel[devices.length];
		for (int i = 0; i < devices.length; i++) {
			deviceStatusLabels[i] = Processor.createStatusPanel(controlPanel, "Device " + devices[i] + ": ", "");
		}

		Processor.createSlider(controlPanel, "Small damage points value", 1, 20, smallDamagePoints,
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						smallDamagePoints = ((JSlider) e.getSource()).getValue();
					}
				});
		Processor.createSlider(controlPanel, "Medium damage points value", 1, 20, mediumDamagePoints,
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						mediumDamagePoints = ((JSlider) e.getSource()).getValue();
					}
				});
		Processor.createSlider(controlPanel, "Large damage points value", 1, 20, largeDamagePoints,
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						largeDamagePoints = ((JSlider) e.getSource()).getValue();
					}
				});
		Processor.createSlider(controlPanel, "Mild chaos points threshold", 1, 10000, mildChaosPoints,
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						mildChaosPoints = ((JSlider) e.getSource()).getValue();
					}
				});
		Processor.createSlider(controlPanel, "Heavy chaos points threshold", 1, 10000, heavyChaosPoints,
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						heavyChaosPoints = ((JSlider) e.getSource()).getValue();
					}
				});

		controlPanel.setPreferredSize(controlPanel.getLayout().minimumLayoutSize(controlPanel));
		controlFrame.pack();
	}

	public void stop(Settings settings) throws ConfigurationException {
		settings.set(SMALL_DAMAGE_POINTS, smallDamagePoints);
		settings.set(MEDIUM_DAMAGE_POINTS, mediumDamagePoints);
		settings.set(LARGE_DAMAGE_POINTS, largeDamagePoints);

		settings.set(MILD_CHAOS_POINTS, mildChaosPoints);
		settings.set(HEAVY_CHAOS_POINTS, heavyChaosPoints);

		stopAllSounds();
		ac.stop();
		controlFrame.setVisible(false);
		controlFrame.dispose();
	}

	public void setDeviceActivity(int deviceIndex, FlowSize flowSize) {
		deviceFlow[deviceIndex] = flowSize;
		deviceStatusLabels[deviceIndex].setText(flowSize.toString().toLowerCase());
	}

	public void process() {
		if (phase != Phase.ACTION) {
			return;
		}
		if (!monsterIntro) {
			return;
		}

		// Monster has been introduced, start measuring activity (damage)
		FlowSize maxFlow = FlowSize.NONE;
		for (int i = 0; i < deviceFlow.length; i++) {
			if (deviceFlow[i].ordinal() > maxFlow.ordinal()) {
				maxFlow = deviceFlow[i];
			}
		}

		int points = 0;
		switch (maxFlow) {
		case LARGE:
			System.out.println("Large damage");
			play(Category.LARGE_DAMAGE, Volume.EFFECT_HIGH);
			points += largeDamagePoints;
			break;
		case MEDIUM:
			System.out.println("Medium damage");
			play(Category.MEDIUM_DAMAGE, Volume.EFFECT_MEDIUM);
			play(Category.CRUMBLE, Volume.EFFECT_MEDIUM);
			points += mediumDamagePoints;
			break;
		case SMALL:
			System.out.println("Small damage");
			play(Category.SMALL_DAMAGE, Volume.EFFECT_LOW);
			play(Category.CRUMBLE, Volume.EFFECT_LOW);
			points += smallDamagePoints;
			break;
		default:
			break;
		}

		long oldScore = score;
		setScore(score + points);

		long second = System.currentTimeMillis() / 1000;
		boolean startedNewChaosLevel = false;

		// Maintain the general chaos
		if (score >= heavyChaosPoints) {
			if (oldScore < heavyChaosPoints) {
				startedNewChaosLevel = true;
				chaosLabel.setText("heavy");
			}

			if (startedNewChaosLevel || (second > lastChaosEffectSecond + 20)) {
				System.out.println("Heavy chaos effect");

				lastChaosEffectSecond = second;
				play(Category.HEAVY_CHAOS, Volume.EFFECT_MEDIUM);
			}
		} else if (score >= mildChaosPoints) {
			if (oldScore < mildChaosPoints) {
				startedNewChaosLevel = true;
				chaosLabel.setText("mild");
			}

			// Trigger something every 15 seconds
			if (startedNewChaosLevel || ((second > lastChaosEffectSecond + 60))) {
				System.out.println("Mild chaos effect");

				lastChaosEffectSecond = second;
				play(Category.MILD_CHAOS, Volume.EFFECT_MEDIUM);
			}
		}
	}

	private void setScore(int newScore) {
		score = newScore;
		scoreLabel.setText(Integer.toString(score));
	}

	private void play(Category category, Volume volume) {
		play(category, volume, false, false);
	}

	private void play(Category category, Volume volume, boolean loop, boolean force) {
		List<Sample> availableSamples = new ArrayList<Sample>(samples.get(category));
		Sample sample = null;

		// Shuffle and find the first sample that isn't playing
		Collections.shuffle(availableSamples);
		for (Sample categorySample : availableSamples) {
			if (force || !isPlaying(category, categorySample)) {
				sample = categorySample;
				break;
			}
		}

		if (sample == null) {
			System.out.println("Already playing all samples in category " + category);
			return;
		}

		final SamplePlayer player = new SamplePlayer(ac, sample);
		player.pause(true);

		// TODO delay

		final Gain gain = new Gain(ac, sample.getNumChannels(), (float) toGain(volume));

		if (loop) {
			player.setLoopType(LoopType.LOOP_FORWARDS);
		}

		beadCategories.put(player, category);
		samplePlayers.add(player);

		gain.addInput(player);
		ac.out.addInput(gain);

		player.setKillListener(new Bead() {
			@Override
			protected void messageReceived(Bead message) {
				// Kill the gain so it can be removed from the audio context
				gain.kill();

				// message is the player
				samplePlayers.remove(message);
			}
		});

		player.pause(false);
	}

	private double toGain(Volume volume) {
		switch (volume) {
		case AMBIENT:
			return 0.3;
		case INTRO:
			return 0.7;
		case FULL:
			return 1.0;
		case EFFECT_LOW:
			// 0.2 - 0.4
			return 0.2 + (random.nextDouble() * 0.2);
		case EFFECT_MEDIUM:
			// 0.4 - 0.7
			return 0.4 + (random.nextDouble() * 0.3);
		case EFFECT_HIGH:
			// 0.7 - 1.0
			return 0.7 + (random.nextDouble() * 0.3);
		default:
			System.err.println("Unknown volume " + volume);
			return 0.0;
		}
	}

	private boolean isPlaying(Category category, Sample sample) {
		List<Bead> activeBeads = new ArrayList<Bead>(samplePlayers.getBeads());

		for (Bead activeBead : activeBeads) {
			if (!(activeBead instanceof SamplePlayer)) {
				continue;
			}

			Category activeBeadCategory = beadCategories.get(activeBead);
			if (activeBeadCategory == null) {
				// Shouldn't happen, but non-fatal
				continue;
			}

			if (activeBeadCategory == category && ((SamplePlayer) activeBead).getSample() == sample) {
				return true;
			}
		}

		return false;
	}

	private void setMonster(Monster monster) {
		this.monster = monster;
	}

	private void handleMonsterIntro() {
		if (phase != Phase.ACTION) {
			return;
		}

		Category category = null;
		switch (monster) {
		case LIZARD:
			category = Category.LIZARD_INTRO;
			break;
		case ROBOT:
			category = Category.ROBOT_INTRO;
			break;
		case OTHER:
			category = Category.OTHER_INTRO;
			break;
		}

		// Play that intro sound!
		play(category, Volume.INTRO, false, true);
		monsterIntro = true;
	}

	private void setPhase(Phase newPhase) {
		if (newPhase == phase) {
			return;
		}
		stopAllSounds();
		setScore(0);
		phase = newPhase;
		monsterIntro = false;
		chaosLabel.setText("peaceful");

		switch (phase) {
		case RECONSTRUCT:
			// Play a random reconstruction sound (they loop)
			play(Category.RECONSTRUCTION, Volume.AMBIENT, true, false);
			break;
		case ACTION:
			// Play a random city sound (they loop)
			play(Category.CITY, Volume.AMBIENT, true, false);
			break;
		default:
			break;
		}
	}

	private void stopAllSounds() {
		samplePlayers.kill();
	}

	/**
	 * Loads files from directories named after all the {@link Category}s.
	 */
	private void loadSamples(File soundsDir, AudioContext ac) {
		for (Category category : Category.values()) {
			File directory = new File(soundsDir, category.toString().toLowerCase());
			if (!directory.exists()) {
				throw new RuntimeException("Sample directory " + directory + " does not exist");
			}

			File[] sampleFiles = directory.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					String lowerName = pathname.getName().toLowerCase();
					return lowerName.endsWith(".wav") || lowerName.endsWith(".mp3");
				}
			});

			for (File sampleFile : sampleFiles) {
				loadSample(ac, category, sampleFile);
			}
		}
	}

	private void loadSample(AudioContext ac, Category category, File sampleFile) {
		try {
			samples.put(category, new Sample(sampleFile.getPath()));
		} catch (IOException | UnsupportedAudioFileException e) {
			throw new RuntimeException(e);
		}
	}
}
