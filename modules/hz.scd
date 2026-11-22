// hz - octaves to hertz: a pitch for an Ndef that only takes a frequency
//
// The library's oscillators take pitch in octaves at oct. An Ndef of
// your own with only a \freq control does not; put this in between.
// With voices, a frequency for each: a chord in octaves becomes a chord
// in Hz.
(
	category: \utilities,
	help: "octaves to Hz: base times 2 to the power of in - for driving a plain \\freq control from a sequencer; with voices, one per voice",
	about: "Turns a pitch in octaves into a frequency in Hz, so a sequencer or quantize can play an Ndef of your own that only has a freq control. 0 at in gives base; each +1 doubles it.",
	params: (
		base: "the frequency in Hz that 0 at in gives",
		in: "the pitch in octaves from base: patch a sequencer, quantize or pitch here; +1 is an octave up"
	),
	poly: { |n| { \base.kr(220 ! n, spec: \freq) * (2 ** \in.kr(0 ! n, spec: [-4, 4])) } }
)
