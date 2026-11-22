// spectrum - a spectrum analyser: what comes in, by frequency
//
// A 2048-point FFT of in, drawn with frequency across on a log scale from
// 20 Hz to the top and level up in dB, from floor to 0 dB. A sine at full
// scale reads 0 dB. Peaks fall back slowly, so they can be read. The signal
// leaves at the output as it came in.
//
// The picture is drawn by the window (NdefRackDisplay), which reads this
// Ndef's bus; floor is a knob for the picture and does nothing to the
// sound. It needs a running server; the picture is read over OSC, so a remote
// one works, only slower.
//
// With voices it passes every voice on; the picture is of the first.
(
	category: \analysis,
	display: \spectrum,
	help: "a spectrum analyser: in, 20 Hz to the top across on a log scale, floor to 0 dB up; passes the signal on; with voices, passes every voice on",
	about: "A spectrum analyser in the module: what comes in, by frequency on a log scale, level in dB. The sound passes through untouched, so it can sit anywhere in a chain to look at a signal.",
	params: (
		floor: "the lowest level the picture shows, in dB - only the picture, not the sound",
		in: "the signal to look at; passed on at the output"
	),
	poly: { |n| {
		\floor.kr(-90, spec: [-120, -30]);
		\in.ar(0 ! n)
	} }
)
