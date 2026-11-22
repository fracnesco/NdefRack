// amp - a gain you can patch: an envelope into gain shapes the sound
//
// With voices, a gain for each: an adsr with as many voices patched into
// gain shapes each voice by its own envelope.
(
	category: \mixers,
	help: "in times gain times level: patch an envelope into gain; expo bends the response from linear towards exponential",
	about: "A gain you can patch: the sound at in comes out times gain times level. Patch an envelope or an lfo into gain to shape or pulse the sound; expo makes fades sound more natural.",
	params: (
		in: "the sound to make louder or softer",
		gain: "how much of the sound gets through: 0 silent, 1 all of it - patch an envelope here",
		expo: "the shape of gain's response: 0 linear, 1 exponential (gain cubed), so low gains are quieter and fades sound smoother",
		level: "a final output level after gain: 1 unchanged, 2 twice as loud"
	),
	poly: { |n| {
		var g = \gain.kr(1 ! n, spec: [0, 1]).max(0);
		var shaped = SelectX.kr(\expo.kr(0 ! n, spec: [0, 1]), [g, g.cubed]);
		// a control signal moves once a block: ramped, so a fast attack clicks less
		\in.ar(0 ! n) * Lag.ar(K2A.ar(shaped), 0.002) * \level.kr(1 ! n, spec: [0, 2])
	} }
)
