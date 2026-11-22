// seq8 - eight steps of values and gates, played by a clock
//
// steps and gates are arrays of eight, so they are multisliders: draw the
// line, then switch steps off in gates. Channel 1 is the value, scaled to
// lo..hi; channel 2 a gate on every step whose gate is up.
//
// With voices, every voice plays the same eight steps with a clock, rate,
// length and range of its own - different lengths phase against each
// other: the values of every voice first, then the gates.
(
	category: \sequencing,
	help: "an 8-step sequencer: steps (values) and gates as multisliders, length, clock or rate, reset; channels: value, gate; with voices, each voice plays the same steps with its own clock, length and range",
	about: "An eight-step sequencer: draw the values, switch steps off in gates, and a clock or its own rate steps through them. Outputs a value and a gate - patch the value into oct and the gate into an envelope.",
	params: (
		steps: "the eight step values, drawn as a multislider; scaled to lo..hi on the way out",
		gates: "which steps send a gate: up on, down silent; all voices share them",
		length: "how many steps play before it starts again",
		lo: "the value of a step drawn at the bottom",
		hi: "the value of a step drawn at the top",
		clock: "steps on each rising edge, on above 0.5 - patch a clock or gate here",
		rate: "its own clock, in steps per second; 0 to follow only the clock input",
		reset: "fires to go back to the first step",
		glen: "how long each gate stays on, in seconds"
	),
	outs: ["value", "gate"],
	pages: [steps: [\steps, \gates, \length, \lo, \hi], clock: [\clock, \rate, \reset, \glen]],
	poly: { |n| {
		var clock = \clock.kr(0 ! n, spec: [0, 1]) > 0.5;
		var trig = (Impulse.kr(\rate.kr(4 ! n, spec: [0, 50, 4]))
			+ (Changed.kr(clock) * clock)) > 0;
		var length = \length.kr(8 ! n, spec: [1, 8, \lin, 1]).round.max(1);
		var step = (PulseCount.kr(trig, \reset.tr(0 ! n)) - 1).max(0) % length;
		var value = Select.kr(step,
			\steps.kr([0, 0.25, 0.5, 0.75, 0.5, 0.25, 0.75, 1], spec: [0, 1]));
		var gate = Select.kr(step, \gates.kr(1 ! 8, spec: [0, 1, \lin, 1]));
		[
			value.linlin(0, 1, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil),
			Trig.kr(trig * gate, \glen.kr(0.05 ! n, spec: [0.001, 1, \exp]))
		].flat
	} }
)
