// onsets - a gate for every note or hit it hears start
//
// An onset detector (Onsets, on a 512-point FFT): each attack in in gives a
// gate glen seconds long - into an adsr's trig, a sequencer's clock, a
// pluck. thresh is how sudden a change has to be: lower finds more, quieter
// ones. Channel 2 is how loud each attack was, -60..0 dB scaled to lo..hi,
// held until the next one.
//
// With voices, a detector for each voice's own channel, with an FFT of its
// own; out come the gates of every voice, then the levels.
(
	category: \listening,
	help: "an onset detector: a gate on every attack in in (thresh: lower finds more), glen long; channel 2 the level of the last attack, -60..0 dB scaled to lo..hi; with voices, one per voice",
	about: "Listens to a sound and gives a gate at every note or hit it hears start, plus how loud that attack was. Patch the gate into a drum, pluck, envelope or sequencer clock to follow a player.",
	params: (
		in: "the sound to listen to",
		thresh: "how sudden a change must be to count: lower finds more and quieter attacks",
		glen: "how long each gate stays on, in seconds",
		lo: "the level output for a quiet attack (-60 dB)",
		hi: "the level output for a full-level attack (0 dB)"
	),
	outs: ["gate", "level"],
	poly: { |n| {
		var in = \in.ar(0 ! n);
		var thresh = \thresh.kr(0.5 ! n, spec: [0.05, 4, \exp]);
		// a buffer per voice: one LocalBuf would be one FFT shared by all
		var onset = n.collect { |i|
			Onsets.kr(FFT(LocalBuf(512), in.asArray[i]), thresh.asArray[i], \rcomplex)
		}.unbubble;
		var level = Amplitude.kr(in, 0.002, 0.1).max(1e-5).ampdb.linlin(-60, 0, 0, 1);
		[
			Trig.kr(onset, \glen.kr(0.05 ! n, spec: [0.001, 1, \exp])),
			Latch.kr(level, onset).linlin(0, 1, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil)
		].flat
	} }
)
