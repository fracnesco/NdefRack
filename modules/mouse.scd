// mouse - where the mouse is, as two signals
//
// With voices, the same mouse read with a lag and a lo..hi per voice: the
// x of every voice first, then the y.
(
	category: \modulation,
	help: "the mouse on the screen: x and y, up is high, scaled to lo..hi - two channels; with voices, a lag and range per voice",
	about: "The mouse position on the screen as two signals: x left to right, y bottom to top. A quick way to play any control by hand - patch x and y wherever you like.",
	params: (
		lag: "smooths the movement, in seconds: higher glides more slowly",
		lo: "the output at the left and bottom edges",
		hi: "the output at the right and top edges"
	),
	outs: ["x", "y"],
	poly: { |n| {
		var lag = \lag.kr(0.1 ! n, spec: [0, 1]);
		var lo = \lo.kr(0 ! n, spec: [-1, 1]), hi = \hi.kr(1 ! n, spec: [-1, 1]);
		[MouseX.kr(0, 1, 0, lag), MouseY.kr(1, 0, 0, lag)]
			.collect { |m| m.linlin(0, 1, lo, hi, nil) }.flat
	} }
)
