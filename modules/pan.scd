// pan - a mono input placed in a stereo pair
//
// With voices, in, pan and level are a value per voice: each voice is placed
// by its own pan and the voices are added into the one pair.
(
	category: \mixers,
	help: "a mono input into a stereo pair: pan, level; with voices, each voice placed by its own pan, added into the pair",
	about: "Places a mono sound in a stereo pair, from left to right. Patch an lfo into pan to move it around.",
	params: (
		in: "the mono sound to place",
		pan: "where it sits: -1 left, 0 centre, 1 right",
		level: "the output level: 1 unchanged"
	),
	poly: { |n| {
		var sig = Pan2.ar(\in.ar(0 ! n), \pan.kr(0 ! n, spec: \pan), \level.kr(1 ! n, spec: [0, 2]));
		if (n > 1) { sig.sum } { sig }
	} }
)
