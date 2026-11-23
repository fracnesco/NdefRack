// pitch - the pitch it hears, in octaves
//
// A pitch tracker (Pitch). Out comes the pitch of in, in octaves from ref,
// the way oct inputs take it: into the oct of a wave whose freq is ref, the
// wave plays the note it hears; through quantize first, the nearest note of
// a scale. Channel 2 is a gate while a pitch is heard: clarity above clarity
// (a steady tone reads about 0.98, noise 0) and the level above floor dB.
// Between notes the last one is held; before the first, it reads 0. smooth
// glides from note to note. One note at a time: a chord gives nothing
// useful.
//
// With voices, a tracker for each voice's own channel, every control a
// value per voice; out come the pitches of every voice, then the gates.
(
	category: \listening,
	help: "a pitch tracker: the pitch of in, in octaves from ref (into a wave at ref, it plays the note); channel 2 a gate while a pitch is heard (clarity, floor dB); holds between notes; with voices, one per voice",
	about: "Listens to a sound and puts out the note it hears, in octaves, plus a gate while a clear pitch is there. Patch it into a wave's oct to double what you play, or through quantize first. One note at a time.",
	params: (
		in: "the sound to listen to - one note at a time",
		ref: "the frequency in Hz that reads 0; set a wave's freq to the same and it plays the note heard",
		clarity: "how clear the tone must be to count: higher ignores noisy sounds",
		floor: "below this level, in dB, nothing counts as a note and the last one is held",
		smooth: "how long it glides from note to note, in seconds"
	),
	outs: ["pitch", "gate"],
	poly: { |n| {
		var in = \in.ar(0 ! n);
		var ref = \ref.kr(220 ! n, spec: \freq);
		// [frequencies, clarities], a channel per voice each
		// 32 bins an octave, not Pitch's 16: at 16 a sine read up to 0.06
		// octaves off; a peak at 0.75 of the strongest, not 0.5: at 0.5 a
		// plucked string read an octave or a twelfth up a fifth of the time;
		// 400 readings a second, not 100: a new note is there in about 50 ms,
		// not 70 (measured 2026-11-23, sines, saws, squares, plucks, formants)
		var pitch = n.collect { |i|
			Pitch.kr(in.asArray[i], 220, 60, 4000, execFreq: 400, maxBinsPerOctave: 32, median: 7,
				ampThreshold: 0.001, peakThreshold: 0.75, clar: 1)
		}.flop.collect(_.unbubble);
		var heard = (pitch[1] > \clarity.kr(0.5 ! n, spec: [0, 1]))
			* (Amplitude.kr(in, 0.01, 0.1).max(1e-5).ampdb > \floor.kr(-50 ! n, spec: [-90, 0]));
		// the last frequency heard; ref itself until there is one
		var freq = Gate.kr(pitch[0], heard);
		freq = Select.kr(freq > 0, [ref, freq]);
		[log2(freq / ref).lag(\smooth.kr(0.02 ! n, spec: [0, 2, 4])), heard].flat
	} }
)
