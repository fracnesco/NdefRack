// slew - a slew limiter: the input, but no faster than rise and fall allow
//
// With voices, a slew for each, every control a value per voice.
(
	category: \modulation,
	help: "slows a signal down: rise and fall are the seconds it takes to go up and to come down; with voices, one per voice",
	about: "A slew limiter: the input, but it can only move so fast, so steps become glides. Separate times for going up and coming down. Use it for portamento on a sequence or to soften a jumpy modulator.",
	params: (
		in: "the signal to slow down - a sequence, a stepped random, a gate",
		rise: "seconds it takes to go up; 0 jumps at once",
		fall: "seconds it takes to come down; 0 jumps at once"
	),
	poly: { |n| {
		LagUD.kr(\in.kr(0 ! n, spec: [-1, 1]), \rise.kr(0.1 ! n, spec: [0, 10, 4]),
			\fall.kr(0.1 ! n, spec: [0, 10, 4]))
	} }
)
