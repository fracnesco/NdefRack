// reverb - a stereo room
//
// FreeVerb, stereo in and out. A stereo cable fills both sides; a mono one
// lands on the left only, so while the right side is silent the left feeds
// both - the way an unpatched right input is normalled on hardware.
//
// With more than two voices at in, they are spread across the pair first
// (Splay), voice 1 on the left; size, damp and mix are for the whole room.
(
	category: \effects,
	help: "FreeVerb, stereo in and out: size, damp, mix; a mono cable feeds both sides; more than two voices are spread across the pair",
	about: "A stereo room reverb (FreeVerb). A mono cable feeds both sides; more than two voices are spread across the pair first. Put it at the end of a chain or on a send bus.",
	params: (
		in: "the sound to put in the room; mono, stereo, or many voices spread across the pair",
		size: "how big the room is: small and tight to large and long",
		damp: "how quickly the high frequencies die away in the tail: up is darker",
		mix: "0 dry only, 1 all reverb"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n.max(2));
		var dry = if (n > 2) { Splay.ar(in) } {
			[in[0], Select.ar(Amplitude.kr(in[1], 0.01, 2) > 0.0001, [in[0], in[1]])]
		};
		var wet = FreeVerb2.ar(dry[0], dry[1], 1,
			\size.kr(0.7, spec: [0, 1]), \damp.kr(0.5, spec: [0, 1]));
		XFade2.ar(dry, wet, \mix.kr(0.35, spec: [0, 1]) * 2 - 1)
	} }
)
