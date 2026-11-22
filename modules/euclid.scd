// euclid - an euclidean rhythm: pulses spread as evenly as they go into steps
//
// With voices it is that many rhythms, each control a value per voice: one
// clock into all of them and a different pulses or steps on each is a
// polyrhythm, a gate per voice.
(
	category: \sequencing,
	help: "pulses spread as evenly as possible over steps, turned by rotate; a gate on every hit, on clock or rate; with voices, a rhythm per voice",
	about: "A Euclidean rhythm: a number of hits spread as evenly as possible over a number of steps, giving many traditional rhythms. Patch a clock into clock and the gate into a drum; several with different settings make polyrhythms.",
	params: (
		clock: "each rising gate (above 0.5) moves one step - patch a clock here",
		rate: "steps on its own clock as well, this many per second; 0 off",
		steps: "how many steps in the pattern",
		pulses: "how many hits are spread over the steps",
		rotate: "turns the pattern, starting it this many steps later",
		glen: "how long each gate stays high, in seconds"
	),
	poly: { |n| {
		var clock = \clock.kr(0 ! n, spec: [0, 1]) > 0.5;
		var trig = (Impulse.kr(\rate.kr(8 ! n, spec: [0, 50, 4]))
			+ (Changed.kr(clock) * clock)) > 0;
		var steps = \steps.kr(8 ! n, spec: [1, 16, \lin, 1]).round.max(1);
		var pulses = \pulses.kr(3 ! n, spec: [0, 16, \lin, 1]).round.clip(0, steps);
		var i = ((PulseCount.kr(trig) - 1).max(0)
			+ \rotate.kr(0 ! n, spec: [0, 15, \lin, 1]).round) % steps;
		// step i is a hit when i * pulses wraps past a multiple of steps
		var hit = ((i * pulses) % steps) < pulses;
		Trig.kr(trig * hit, \glen.kr(0.05 ! n, spec: [0.001, 1, \exp]))
	} }
)
