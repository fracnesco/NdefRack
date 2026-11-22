// formant - a vowel: three resonances, from a to e to i to o to u
//
// Three formants of a tenor voice, their frequencies, levels and widths
// morphed by vowel (0 a, 1 e, 2 i, 3 o, 4 u). shift moves all three, in
// octaves, for a smaller or a bigger throat; q narrows them. Feed it
// something rich: a saw, noise, a pulse. With voices, a vowel filter for
// each, every control a value per voice.
(
	category: \filters,
	help: "a vowel filter: vowel morphs a e i o u (0..4), shift moves every formant in octaves, q narrows them",
	about: "A vowel filter: three resonances of a voice that morph from a to e, i, o and u, making any rich sound talk. Feed it a saw, a pulse or noise and patch an lfo or sequencer into vowel.",
	params: (
		vowel: "the vowel: a, e, i, o, u - in between morphs smoothly",
		shift: "moves every resonance in octaves: up for a smaller throat, down for a bigger one",
		q: "how narrow the resonances are: higher is sharper and more vocal",
		in: "the sound to filter - something rich: saw, pulse, noise",
		level: "the output level"
	),
	labels: (vowel: ["a", "e", "i", "o", "u"]),
	poly: { |n| {
		// per vowel: frequencies, amplitudes, bandwidths of the three formants
		var table = [
			[[650, 1080, 2650], [1, 0.5, 0.45], [80, 90, 120]],
			[[400, 1700, 2600], [1, 0.2, 0.25], [70, 80, 100]],
			[[290, 1870, 2800], [1, 0.18, 0.12], [40, 90, 100]],
			[[400, 800, 2600], [1, 0.3, 0.25], [40, 80, 100]],
			[[350, 600, 2700], [1, 0.1, 0.14], [40, 60, 100]]
		];
		var vowel = \vowel.kr(0 ! n, spec: [0, 4]);
		var shift = 2 ** \shift.kr(0 ! n, spec: [-2, 2]);
		var q = \q.kr(1 ! n, spec: [0.25, 4, \exp]);
		var in = \in.ar(0 ! n);
		var bands = 3.collect { |k|
			var f = (SelectX.kr(vowel, table.collect { |v| v[0][k] }) * shift).clip(20, 18000);
			var amp = SelectX.kr(vowel, table.collect { |v| v[1][k] });
			var bw = SelectX.kr(vowel, table.collect { |v| v[2][k] }) * shift / q;
			Resonz.ar(in, f, (bw / f).clip(0.005, 1)) * amp
		};
		bands.sum * \level.kr(3 ! n, spec: [0, 8])
	} }
)
