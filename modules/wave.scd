// wave - the plain oscillator: one knob from sine to triangle to saw to square
//
// With voices it is that many oscillators, each control a value per voice -
// a chord into oct plays one note on each. detune, there only with more than
// one voice, spreads them across that many semitones, lowest to highest.
(
	category: \oscillators,
	help: "sine, triangle, saw and square, morphed by shape; pulse width, oct and FM inputs; with voices, detune spreads them",
	about: "The plain oscillator: one knob from sine to triangle, saw and square, with pulse width and FM. A starting point for most patches - with voices, a chord into oct plays one note per voice and detune thickens them.",
	params: (
		detune: "with voices: spreads them across this many semitones, lowest to highest",
		freq: "the pitch, in Hz",
		oct: "moves the pitch in octaves: 1 an octave up - patch a melody or a sequencer here",
		fm: "frequency modulation input - patch another oscillator here",
		fmamt: "how much fm bends the pitch; 0 none",
		pw: "the square's pulse width: 0.5 square, narrower thinner; only heard near the square shape",
		shape: "the waveform: sine, triangle, saw, square - in between morphs between neighbours",
		amp: "the output level"
	),
	labels: (shape: ["sine", "triangle", "saw", "square"]),
	poly: { |n| {
		var spread = if (n > 1) { (0 .. n - 1) / (n - 1) - 0.5 * \detune.kr(0, spec: [0, 2]) / 12 } { 0 };
		var freq = \freq.kr(220 ! n, spec: \freq) * (2 ** (\oct.kr(0 ! n, spec: [-4, 4]) + spread));
		var f = (freq * (1 + (\fm.ar(0 ! n) * \fmamt.kr(0 ! n, spec: [0, 1])))).max(0);
		var pw = \pw.kr(0.5 ! n, spec: [0.02, 0.98]);
		SelectX.ar(\shape.kr(0 ! n, spec: [0, 3]), [
			SinOsc.ar(f), LFTri.ar(f), Saw.ar(f), Pulse.ar(f, pw)
		]) * \amp.kr(0.2 ! n, spec: \amp)
	} }
)
