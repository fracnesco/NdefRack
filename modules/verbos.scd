// verbos - eight harmonics, and a window that scans across them
//
// After the Verbos Harmonic Oscillator: the first eight partials of one
// pitch, each with its own level, and a window swept across them. Wide
// open, the levels alone shape the tone; narrowed, scan picks out one
// harmonic at a time and slides between neighbours.
//
// levels is an array of eight: patch an 8-channel source into it (lfo8)
// and every partial moves on its own.
//
// With voices it is that many oscillators, each built on its own, every
// control a value per voice but levels, which all the voices share.
(
	category: \oscillators,
	help: "eight sine partials, a level each, a window swept across them (scan, width), odd and even balance; oct and FM inputs; with voices, levels shared by all",
	about: "After the Verbos Harmonic Oscillator: the first eight harmonics of one pitch, each with its own level, and a window that scans across them. Narrow the window and sweep scan for vowel-like, glassy movement.",
	params: (
		levels: "the level of each of the eight harmonics - patch an 8-channel source (lfo8) to move each one",
		scan: "where the window sits: 0 the first harmonic, 1 the eighth",
		width: "how many harmonics the window holds open: 0 one, 1 all eight",
		odd: "the level of the odd harmonics (1, 3, 5, 7)",
		even: "the level of the even harmonics (2, 4, 6, 8)",
		freq: "the pitch, in Hz",
		oct: "moves the pitch in octaves: 1 an octave up - patch a melody or a sequencer here",
		fm: "frequency modulation input - patch an audio oscillator here",
		fmamt: "how much fm bends the pitch; 0 none",
		amp: "the output level"
	),
	pages: [harmonics: [\levels, \scan, \width, \odd, \even], pitch: [\freq, \oct, \fm, \fmamt, \amp]],
	poly: { |n| {
		var harm = (1..8);
		var freqs = (\freq.kr(110 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).asArray;
		var fms = (\fm.ar(0 ! n) * \fmamt.kr(0 ! n, spec: [0, 4])).asArray;
		var levels = \levels.kr(1 / harm, spec: [0, 1]).lag(0.02);
		var scans = \scan.kr(0 ! n, spec: [0, 1]).asArray;
		// how many harmonics the window holds open, one to all eight
		var opens = \width.kr(1 ! n, spec: [0, 1]).linlin(0, 1, 1, 8).asArray;
		var odds = \odd.kr(1 ! n, spec: [0, 1]).asArray;
		var evens = \even.kr(1 ! n, spec: [0, 1]).asArray;
		var amps = \amp.kr(0.3 ! n, spec: \amp).asArray;
		// eight partials are an array already, so each voice is built on its own
		n.collect { |v|
			var freq = freqs[v];
			// a flat top as wide as asked, and one harmonic of slope either side
			var window = (opens[v] - (harm - 1 - (scans[v] * 7)).abs).clip(0, 1);
			var gains = levels * window * harm.collect { |h| if (h.odd) { odds[v] } { evens[v] } };
			// nothing above nyquist
			gains = gains * (freq * harm < (SampleRate.ir * 0.45));
			(SinOsc.ar(freq * harm * (1 + fms[v])) * gains).sum / gains.sum.max(1)
				* amps[v]
		}.unbubble
	} }
)
