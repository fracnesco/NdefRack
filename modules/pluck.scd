// pluck - a string: Karplus-Strong, plucked by a gate or by its own clock
//
// With voices it is that many strings, each control a value per voice and
// each plucked with a noise of its own: a gate with voices into trig plucks
// each one on its own.
(
	category: \oscillators,
	help: "a Karplus-Strong string: trig plucks it (the button, or a gate patched in), or rate on its own clock; decay and damp; with voices, a string per voice",
	about: "A plucked string (Karplus-Strong): a burst of noise rings at a pitch and dies away. Pluck it with a gate from a sequencer or clock, or let rate run it on its own; patch a pitch into oct.",
	params: (
		freq: "the pitch, in Hz",
		oct: "moves the pitch in octaves: +1 an octave up - patch a sequencer or quantize here",
		rate: "plucks per second on its own clock; 0 off, so only trig plucks it",
		trig: "plucks the string when it goes above 0.5 - the button, or a gate from a sequencer or clock",
		decay: "how long the string rings, in seconds",
		damp: "how dark the string is: higher dulls it faster, below 0 it turns thin and bright",
		amp: "the output level"
	),
	poly: { |n| {
		var freq = (\freq.kr(220 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).max(10);
		var rate = \rate.kr(0 ! n, spec: [0, 20, 4]);
		var gate = \trig.kr(0 ! n) > 0.5;
		// Impulse fires once at the start even at rate 0, which is not a pluck
		var trig = (Impulse.kr(rate) * (rate > 0)) + (Changed.kr(gate) * gate);
		Pluck.ar(WhiteNoise.ar(1 ! n), trig, 0.1, freq.reciprocal,
			\decay.kr(2 ! n, spec: [0.1, 20, \exp]), \damp.kr(0.4 ! n, spec: [-0.5, 0.9]))
			* \amp.kr(0.5 ! n, spec: \amp)
	} }
)
