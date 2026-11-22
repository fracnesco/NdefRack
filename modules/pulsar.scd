// pulsar - pulsar synthesis: a short burst at formant Hz, freq times a second
//
// After Curtis Roads. Each period of the fundamental starts a pulsaret -
// cycles periods of a wave at formant Hz, under a window - and is silent for
// the rest of it. Below about 20 Hz the pulsarets are a rhythm; above, a
// pitch with a formant: freq is the note, formant its colour. shift moves the
// formant in octaves the way oct moves the note; more cycles narrow it.
// shape morphs the wave - sine, triangle, saw, square, worked out from the
// pulsaret's phase, so saw and square alias at high formants - and window
// the envelope: a rectangle, a hann, a sharp attack with an exponential
// decay. burst and rest mask pulses in turn - burst sound, then rest are
// left out - for rhythms, or subharmonics at audio rates; prob lets each
// one through by chance.
//
// With voices it is that many pulsars, each control a value per voice: the
// same freq with a formant each is Roads' pulsar with several formants.
(
	category: \oscillators,
	help: "pulsar synthesis: a pulsaret of cycles periods at formant Hz under a window, fired freq times a second - a rhythm below 20 Hz, a pitch above; shift moves the formant in octaves; shape, window; burst/rest and prob mask pulses",
	about: "Pulsar synthesis: short bursts of a wave, fired freq times a second. Slow, they are a rhythm; fast, a pitched tone whose colour comes from formant. burst, rest and prob leave pulses out for rhythms and subharmonics.",
	params: (
		freq: "how many pulses per second: below about 20 a rhythm, above the pitch",
		oct: "moves freq in octaves - patch a sequencer or quantize here",
		formant: "the frequency inside each burst, in Hz: the tone's colour",
		shift: "moves the formant in octaves - patch modulation here",
		cycles: "how many waves fill each burst: more is longer and narrower in colour",
		shape: "the wave inside the burst: sine, triangle, saw, square - in between morphs",
		window: "the envelope of each burst: rectangle (hard), hann (soft), exp decay (a sharp attack)",
		burst: "how many pulses sound in a row before the rest",
		rest: "how many pulses are then left out: 0 none",
		prob: "the chance each pulse sounds: 1 all, lower drops them at random",
		amp: "the output level"
	),
	labels: (shape: ["sine", "triangle", "saw", "square"], window: ["rectangle", "hann", "exp decay"]),
	pages: [tone: [\freq, \oct, \formant, \shift, \cycles, \shape, \window], pulses: [\burst, \rest, \prob, \amp]],
	poly: { |n| {
		var fund = (\freq.kr(110 ! n, spec: [0.5, 2000, \exp]) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).max(0);
		var formant = (\formant.kr(1000 ! n, spec: \freq) * (2 ** \shift.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var cycles = \cycles.kr(1 ! n, spec: [1, 8]);
		var trig = Impulse.ar(fund);
		// 0..1 across the pulsaret, then on past 1 until the next one starts
		var phase = Sweep.ar(trig, formant / cycles);
		var on = phase < 1;
		var p = phase.min(1);
		var ph = (p * cycles).wrap(0, 1);
		var wave = SelectX.ar(\shape.kr(0 ! n, spec: [0, 3]), [
			sin(ph * 2pi),
			((ph + 0.25).wrap(0, 1) * 4 - 1).fold2(1),
			(ph + 0.5).wrap(0, 1) * 2 - 1,
			(ph < 0.5) * 2 - 1
		]);
		var window = SelectX.ar(\window.kr(1 ! n, spec: [0, 2]), [
			on,
			0.5 - (0.5 * cos(p * 2pi)),
			(1 - p).squared.squared
		]);
		var burst = \burst.kr(1 ! n, spec: [1, 16, \lin, 1]).round.max(1);
		var rest = \rest.kr(0 ! n, spec: [0, 16, \lin, 1]).round.max(0);
		var pass = (Stepper.ar(trig, 0, 0, burst + rest - 1) < burst)
			* (TRand.ar(0, 1, trig) < \prob.kr(1 ! n, spec: [0, 1]));
		LeakDC.ar(wave * window * on * pass) * \amp.kr(0.2 ! n, spec: \amp)
	} }
)
