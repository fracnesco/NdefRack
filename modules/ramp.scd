// ramp - a function generator: up at rise, down at fall, once or round and round
//
// After the Befaco Rampage and the Make Noise Maths. trig starts it rising;
// at the top it turns and falls - or waits there while the gate is held, and
// falls when the gate lets go. cycle starts it again every time it reaches
// the bottom, which makes it an LFO whose rise and fall are set apart. The
// knobs are read every step, so a modulated rise bends the ramp as it goes.
// shape bends both slopes, log (-1) through linear (0) to exponential (1).
// Channel 1 is the function, scaled to lo..hi; channel 2 a trigger at the
// end of every cycle, to start another ramp from.
//
// With voices it is that many function generators, every control a value
// per voice: the functions of every voice first, then the end triggers.
(
	category: \modulation,
	help: "a function generator (Rampage, Maths): trig rises at rise and falls at fall, waiting at the top while the gate is held; cycle loops it; shape bends it log..exp; channels: function (lo..hi), end-of-cycle trigger; with voices, one per voice",
	about: "A function generator: a gate starts a rise then a fall, holding at the top while the gate stays on. Turn cycle on and it loops as an lfo with separate rise and fall. A trigger at each end can start another.",
	params: (
		trig: "starts a ramp when it goes above 0.5; while held, the ramp waits at the top",
		rise: "how long the way up takes, in seconds",
		fall: "how long the way down takes, in seconds",
		cycle: "on: it starts again each time it reaches the bottom, like an lfo",
		shape: "bends both slopes: -1 log, 0 straight, 1 exponential",
		lo: "the value at the bottom",
		hi: "the value at the top - set it below lo to turn it upside down"
	),
	outs: ["function", "end"],
	poly: { |n| {
		var gate = \trig.kr(0 ! n) > 0.5;
		var rise = \rise.kr(0.1 ! n, spec: [0.001, 10, \exp]);
		var fall = \fall.kr(0.5 ! n, spec: [0.001, 10, \exp]);
		var cycle = \cycle.kr(0 ! n, spec: [0, 1, \lin, 1]) > 0.5;
		var state = LocalIn.kr(2 * n);
		// the value, and which way it goes: 1 up, -1 down, 0 still - n of each
		var s = state[0 .. n - 1].unbubble, d = state[n ..].unbubble;
		var dt = ControlDur.ir;
		var fire = Changed.kr(gate) * gate;
		var top, bottom, start;
		s = s + ((d > 0) * dt / rise) - ((d < 0) * dt / fall);
		top = (d > 0) * (s >= 1);
		bottom = (d < 0) * (s <= 0);
		s = s.clip(0, 1);
		d = Select.kr((top + bottom) > 0, [d, 0]);
		// still at the top with the gate gone: down
		d = Select.kr((d.abs < 0.5) * (s >= 1) * (1 - gate), [d, -1]);
		// a trigger, or the bottom reached with cycle on: up
		start = fire + ((d.abs < 0.5) * (s <= 0) * cycle);
		d = Select.kr(start > 0, [d, 1]);
		LocalOut.kr([s, d].flat);
		[
			s.lincurve(0, 1, 0, 1, \shape.kr(0 ! n, spec: [-1, 1]) * 4)
				.linlin(0, 1, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil),
			Trig.kr(bottom, 0.01)
		].flat
	} }
)
