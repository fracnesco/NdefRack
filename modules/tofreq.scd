// tofreq - a modulator onto a frequency, the way the ear hears it
//
// A cable replaces the knob it plugs into, so an lfo of -1..1 into a
// cutoff of 1200 Hz is a cutoff of -1..1 Hz. This puts it where it should
// be: in, from inlo..inhi, becomes lo..hi Hz, evenly in octaves. With
// voices, every voice its own in and its own ranges.
(
	category: \utilities,
	help: "maps in from inlo..inhi to lo..hi Hz exponentially - an lfo or an envelope straight onto a frequency knob; with voices, one per voice",
	about: "Turns a modulator into a frequency the way the ear hears pitch: evenly in octaves between lo and hi Hz. Put it between an lfo or envelope and a frequency knob, which a cable would otherwise replace outright.",
	params: (
		in: "the modulator to turn into a frequency - an lfo, an envelope",
		inlo: "the input value that comes out as lo",
		inhi: "the input value that comes out as hi",
		lo: "the frequency inlo becomes, in Hz",
		hi: "the frequency inhi becomes, in Hz"
	),
	poly: { |n| {
		\in.kr(0 ! n, spec: [-1, 1]).linexp(
			\inlo.kr(-1 ! n, spec: [-10, 10]), \inhi.kr(1 ! n, spec: [-10, 10]),
			\lo.kr(200 ! n, spec: \freq), \hi.kr(2000 ! n, spec: \freq), nil)
	} }
)
