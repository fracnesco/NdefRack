// sum - the voices of a cable, added into one signal
//
// It takes as many voices as the cable patched into in brings. comp 1
// divides by the square root of how many there are, so the level stays
// about the same whatever the count; comp 0 adds them as they are.
(
	category: \mixers,
	help: "the voices of the cable into in, added into one signal: comp 1 keeps the level as the count changes, level; takes as many voices as the cable brings",
	about: "Adds all the voices of a cable into one mono signal. comp keeps the level about the same however many voices there are. It takes as many voices as the cable brings.",
	params: (
		in: "a cable with voices - the module widens to match it",
		comp: "1 keeps the level steady as the voice count changes, 0 adds them as they are",
		level: "the output level"
	),
	follow: \in,
	poly: { |n| {
		var in = \in.ar(0 ! n);
		in.asArray.sum * (n.sqrt.reciprocal ** \comp.kr(1, spec: [0, 1])) * \level.kr(1, spec: [0, 2])
	} }
)
