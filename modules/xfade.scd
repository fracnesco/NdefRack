// xfade - from one input to another, equal power
//
// With voices, a crossfade for each, a, b and x a value per voice.
(
	category: \mixers,
	help: "an equal-power crossfade from a (x = -1) to b (x = 1); with voices, one per voice",
	about: "An equal-power crossfade from one input to another. Patch two sounds into a and b and sweep x by hand or with an lfo to move between them.",
	params: (
		a: "the first sound, heard alone at x = -1",
		b: "the second sound, heard alone at x = 1",
		x: "where the fade is: -1 all a, 0 both evenly, 1 all b - patch an lfo to sweep"
	),
	poly: { |n| { XFade2.ar(\a.ar(0 ! n), \b.ar(0 ! n), \x.kr(0 ! n, spec: [-1, 1])) } }
)
