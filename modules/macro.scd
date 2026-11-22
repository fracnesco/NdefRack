// macro - a knob that is a signal: one value for as many inputs as you patch
//
// With voices, a value per voice, each with its own lo..hi - a channel each.
(
	category: \modulation,
	help: "a knob whose value is a signal: patch it into several inputs and turn them all at once; lo..hi; with voices, a value per voice",
	about: "A knob that is a signal: patch it into several inputs at once and one turn moves them all together. lo and hi set where its range lands.",
	params: (
		value: "the knob: 0 gives lo, 1 gives hi",
		lo: "the output when value is 0",
		hi: "the output when value is 1 - set it below lo to turn the knob around"
	),
	poly: { |n| {
		\value.kr(0.5 ! n, spec: [0, 1]).lag(0.05)
			.linlin(0, 1, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil)
	} }
)
