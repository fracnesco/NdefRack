// values - a value for each voice, set by hand
//
// A slider per voice, and out comes a channel per voice, scaled to lo..hi.
// Patched into a module with as many voices, every voice gets a setting of
// its own: a detune, a pan, a clock rate. The sliders start spread evenly
// from lo to hi.
(
	category: \utilities,
	help: "one value per voice from the sliders (v), scaled to lo..hi: a setting of its own for each voice of a module with voices",
	about: "A slider per voice, each sending its own value: patched into a module with as many voices, every voice gets a setting of its own - a detune, a pan, a rate. The sliders start spread from lo to hi.",
	params: (
		v: "one slider per voice, scaled to lo..hi on the way out",
		lo: "the value a slider at the bottom gives",
		hi: "the value a slider at the top gives"
	),
	sliders: [\v],
	poly: { |n| {
		var ramp = if (n > 1) { (0 .. n - 1) / (n - 1) } { 0.5 };
		\v.kr(ramp, spec: [0, 1]).linlin(0, 1, \lo.kr(-1, spec: [-1, 1]), \hi.kr(1, spec: [-1, 1]), nil)
	} }
)
