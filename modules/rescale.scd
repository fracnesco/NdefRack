// rescale - one range into another, in a straight line
//
// With voices, every voice its own in and its own ranges.
(
	category: \utilities,
	help: "maps in from inlo..inhi to lo..hi, linearly - an attenuverter and an offset in one; with voices, one per voice",
	about: "Moves a signal from one range into another along a straight line, so a modulator can be made smaller, bigger, upside down or offset. Put it between an lfo or envelope and the control it moves.",
	params: (
		in: "the signal to rescale - an lfo, an envelope, any modulation",
		inlo: "the input value that comes out as lo",
		inhi: "the input value that comes out as hi",
		lo: "what inlo becomes at the output",
		hi: "what inhi becomes at the output - set it below lo to turn the signal upside down"
	),
	poly: { |n| {
		\in.kr(0 ! n, spec: [-1, 1]).linlin(
			\inlo.kr(-1 ! n, spec: [-10, 10]), \inhi.kr(1 ! n, spec: [-10, 10]),
			\lo.kr(0 ! n, spec: [-10, 10]), \hi.kr(1 ! n, spec: [-10, 10]), nil)
	} }
)
