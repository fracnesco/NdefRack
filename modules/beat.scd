// beat - the beat it hears, as a clock
//
// A beat tracker (BeatTrack, on a 1024-point FFT). Out come gates on the
// beat, the eighths and the sixteenths - the first three channels of clock -
// and the tempo in bpm on channel 4: through pick into clock's bpm, the
// clock follows. It needs a few seconds of music with a beat to lock on.
// While hold is held it keeps the tempo and the phase it has.
//
// With voices, a tracker for each voice's own channel, with an FFT of its
// own; out come the beats of every voice, then the eighths, the sixteenths,
// the tempos.
(
	category: \listening,
	help: "a beat tracker: gates on the beat, 8ths and 16ths of in (like clock), tempo in bpm on channel 4; hold keeps tempo and phase; glen; with voices, a tracker per voice",
	about: "A beat tracker: it listens to music and puts out gates on the beat, the eighths and the sixteenths, plus the tempo in bpm. Patch the gates into sequencers, or the bpm through pick into clock's bpm; it needs a few seconds of music to lock on.",
	params: (
		in: "the music to listen to - something with a clear beat",
		hold: "while open (above 0.5) it keeps the tempo and phase it has and stops following",
		glen: "how long each gate stays high, in seconds"
	),
	outs: ["beat", "8th", "16th", "bpm"],
	poly: { |n| {
		var in = \in.ar(0 ! n).asArray, hold = \hold.kr(0 ! n).asArray;
		var glen = \glen.kr(0.05 ! n, spec: [0.001, 1, \exp]).asArray;
		// a buffer per voice: one LocalBuf would be one FFT shared by all
		n.collect { |i|
			var beat = BeatTrack.kr(FFT(LocalBuf(1024), in[i]), hold[i] > 0.5);
			[Trig.kr(beat[0], glen[i]), Trig.kr(beat[1], glen[i]), Trig.kr(beat[2], glen[i]), beat[3] * 60]
		}.flop.flat
	} }
)
