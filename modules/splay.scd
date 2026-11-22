// splay - the voices of a cable, spread across a stereo pair
//
// It takes as many voices as the cable patched into in brings: voice 1 on
// the left, the last on the right, the rest in between (Splay). spread 0
// puts them all in the middle, center moves the lot, and the level stays
// about the same however many there are. The way to hear a module with
// voices: a cable with voices into a mono input brings only the first.
(
	category: \mixers,
	help: "the voices of the cable into in, spread across a stereo pair (Splay): spread, center, level; takes as many voices as the cable brings",
	about: "Spreads all the voices of a cable across a stereo pair, the first on the left and the last on the right. The way to hear a module with voices in stereo; it takes as many voices as the cable brings.",
	params: (
		in: "a cable with voices - the module widens to match it",
		spread: "how wide the voices are spread: 0 all in the middle, 1 left to right",
		level: "the output level",
		center: "moves the whole spread left or right: -1 left, 1 right"
	),
	follow: \in,
	poly: { |n| {
		Splay.ar(\in.ar(0 ! n), \spread.kr(1, spec: [0, 1]), \level.kr(1, spec: [0, 2]),
			\center.kr(0, spec: [-1, 1]))
	} }
)
