// mixer - four mono inputs into a stereo pair, a level and a pan each
//
// levels and pans are arrays of four, so they are multisliders - and a
// four channel source patched into either moves all four at once.
//
// With voices, each input takes that many: an input's voices are added,
// divided by the square root of how many there are so the level stays
// about the same, and the sum goes to that input's pan position. levels,
// pans and master stay one set for the whole mixer.
(
	category: \mixers,
	help: "four mono inputs, a level and a pan each (arrays of four), into one stereo pair; master; with voices, each input's voices summed onto its pan",
	about: "Four mono inputs mixed into a stereo pair, each with its own level and pan. Patch your sources in, balance them and send the pair to the speakers or a reverb.",
	params: (
		in1: "the first sound to mix",
		in2: "the second sound to mix",
		in3: "the third sound to mix",
		in4: "the fourth sound to mix",
		levels: "the level of each of the four inputs: 1 unchanged",
		pans: "where each input sits: -1 left, 0 centre, 1 right",
		master: "the level of the whole mix"
	),
	poly: { |n| {
		var ins = [\in1.ar(0 ! n), \in2.ar(0 ! n), \in3.ar(0 ! n), \in4.ar(0 ! n)];
		var levels = \levels.kr(1 ! 4, spec: [0, 1.5]).lag(0.02);
		var pans = \pans.kr([-0.5, -0.15, 0.15, 0.5], spec: [-1, 1]).lag(0.02);
		if (n > 1) { ins = ins.collect { |in| in.sum * n.sqrt.reciprocal } };
		Mix(Pan2.ar(ins * levels, pans)) * \master.kr(0.8, spec: [0, 1.5])
	} }
)
