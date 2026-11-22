// resonator - eight modes that ring when something strikes them
//
// A body, struck. The modes are harmonic at structure 0 and move to a
// free bar's inharmonic ratios at 1 - from string to marimba to bell.
// bright keeps the upper modes loud and long; down, they die first, as
// they do in wood. Excite it from in, or with the trig button.
//
// With voices it is that many bodies, each control a value per voice, each
// built on its own (a bank of eight modes per voice) with a strike of its own.
(
	category: \filters,
	help: "eight resonant modes at freq (oct adds octaves): structure harmonic to inharmonic, decay, bright; excited by in or by trig; with voices, a body per voice",
	about: "A struck body of eight ringing modes: like a string at structure 0, moving to a marimba bar and bell-like tones at 1. Hit it with trig, or feed a sound or noise into in and it rings along. Good after drums, clicks and short noises.",
	params: (
		freq: "the pitch of the lowest mode, in Hz",
		oct: "moves the pitch in octaves: 1 an octave up - patch a melody or a sequencer here",
		structure: "0 harmonic modes like a string, 1 the inharmonic ratios of a free bar; in between morphs",
		bright: "how loud and long the upper modes ring; down, they fade first, as in wood",
		decay: "how long it rings, in seconds",
		trig: "strikes the body on its rising edge, on above 0.5 - press it, or patch a clock or gate",
		in: "a sound that excites the modes - noise bursts, drums, anything percussive",
		level: "the output level"
	),
	labels: (structure: ["harmonic", "inharmonic"]),
	poly: { |n| {
		var freqs = (\freq.kr(220 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 8000).asArray;
		var structures = \structure.kr(0 ! n, spec: [0, 1]).asArray;
		var brights = \bright.kr(0.5 ! n, spec: [0, 1]).asArray;
		var decays = \decay.kr(2 ! n, spec: [0.05, 20, \exp]).asArray;
		var gates = (\trig.kr(0 ! n) > 0.5).asArray;
		var ins = \in.ar(0 ! n).asArray;
		var levels = \level.kr(0.05 ! n, spec: [0, 0.5]).asArray;
		var bar = [1, 2.756, 5.404, 8.933, 13.344, 18.638, 24.815, 31.873];
		// eight modes are an array already, so each voice is built on its own
		n.collect { |v|
			var structure = structures[v], bright = brights[v], decay = decays[v], gate = gates[v];
			var ratios = ((1..8) * (1 - structure)) + (bar * structure);
			var modes = freqs[v] * ratios;
			// above nyquist a mode is silent, not folded back
			var amps = (0..7).collect { |i| bright.max(0.05) ** i }
				* (modes < (SampleRate.ir * 0.45));
			var times = (0..7).collect { |i| decay / (1 + (i * (1 - bright) * 1.5)) };
			var strike = Decay2.ar(K2A.ar(Changed.kr(gate) * gate), 0.0005, 0.01) * WhiteNoise.ar(0.5);
			DynKlank.ar(`[modes, amps, times], ins[v] + strike) * levels[v]
		}.unbubble
	} }
)
