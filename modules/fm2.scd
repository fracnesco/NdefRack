// fm2 - two operators: a modulator, with feedback, bending a carrier's phase
//
// index is how far the carrier's phase is pushed, in radians; an envelope
// patched into it is the usual way to make the sound speak. pm takes an
// outside modulator as well, scaled by pmamt. With voices, a pair of
// operators for each, every control a value per voice.
(
	category: \oscillators,
	help: "two-operator phase modulation: ratio, index, modulator feedback; oct input, and pm for an outside modulator",
	about: "Two-operator FM: a sine modulator, with feedback, bending the phase of a sine carrier, from soft bells to harsh metallic tones. Patch an envelope into index to make each note speak, and a pitch into oct.",
	params: (
		freq: "the carrier's pitch, in Hz",
		oct: "moves the pitch up or down in octaves from freq - patch a sequencer here",
		ratio: "the modulator's frequency as a multiple of the carrier's: whole numbers are harmonic, others clangorous",
		fb: "the modulator feeding back on itself: more is brighter and noisier",
		index: "how hard the modulator pushes the carrier, in radians: more is brighter - patch an envelope here",
		pm: "an outside signal to modulate the carrier's phase - another oscillator",
		pmamt: "how hard the outside signal at pm pushes the carrier, in radians",
		amp: "the output level"
	),
	poly: { |n| {
		var freq = \freq.kr(220 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]));
		var mod = SinOscFB.ar(freq * \ratio.kr(2 ! n, spec: [0.25, 16, \exp]),
			\fb.kr(0 ! n, spec: [0, 1.5]));
		var phase = (mod * \index.kr(1 ! n, spec: [0, 12, 3]))
			+ (\pm.ar(0 ! n) * \pmamt.kr(0 ! n, spec: [0, 12, 3]));
		SinOsc.ar(freq, phase) * \amp.kr(0.2 ! n, spec: \amp)
	} }
)
