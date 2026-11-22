// ring - ring modulation: the input times a carrier
//
// The carrier is the module's own sine at freq, as loud as osc, plus
// whatever is patched into car - turn osc down to use only that. With
// voices, a modulator for each, every control a value per voice.
(
	category: \effects,
	help: "in times a carrier: the built-in sine at freq (osc) plus anything patched into car; mix with the dry input; with voices, one per voice",
	about: "Ring modulation: the input multiplied by a carrier, giving metallic, bell-like or robotic sidebands. The carrier is a built-in sine plus anything patched into car.",
	params: (
		in: "the sound to modulate",
		freq: "the built-in sine carrier's frequency, in Hz",
		osc: "how loud the built-in sine is in the carrier; 0 to use only what is patched into car",
		car: "an extra carrier signal, added to the sine - patch another oscillator here",
		mix: "0 the dry input, 1 all ring-modulated"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		var car = (SinOsc.ar(\freq.kr(440 ! n, spec: \freq)) * \osc.kr(1 ! n, spec: [0, 1])) + \car.ar(0 ! n);
		XFade2.ar(in, in * car, \mix.kr(1 ! n, spec: [0, 1]) * 2 - 1)
	} }
)
