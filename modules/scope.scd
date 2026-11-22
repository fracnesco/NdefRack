// scope - an oscilloscope: what comes in, drawn in the module, and passed on
//
// Two traces, one per channel of in: channel 1 blue, channel 2 orange. A
// trace starts each time channel 1 rises through zero, so a steady wave
// stands still; when nothing crosses zero for a quarter of a second it
// runs free. time is how many seconds the width shows, gain how far up a
// value of 1 reaches. The signal leaves at the output as it came in, so a
// scope can sit in the middle of a chain.
//
// The picture is drawn by the window (NdefRackDisplay), which reads this
// Ndef's bus; time and gain are knobs for the picture and do nothing to
// the sound. It needs a running server; the picture is read over OSC, so a
// remote one works, only slower.
//
// With voices it passes every voice on; the picture draws a trace for each
// of the first eight.
(
	category: \analysis,
	display: \scope,
	help: "an oscilloscope: two traces of in, time seconds across, gain up, triggered as channel 1 rises through zero; passes the signal on; with voices, passes every voice on",
	about: "An oscilloscope in the module: two traces of what comes in, held still on a steady wave. The sound passes through untouched, so it can sit anywhere in a chain to look at a signal.",
	params: (
		time: "how many seconds the picture's width shows - only the picture, not the sound",
		gain: "zooms the picture up and down - only the picture, not the sound",
		in: "the signal to look at; channel 1 and 2 drawn as two traces, passed on at the output"
	),
	poly: { |n| {
		\time.kr(0.02, spec: [0.001, 1, \exp]);
		\gain.kr(1, spec: [0.1, 20, \exp]);
		\in.ar(0 ! n.max(2))
	} }
)
