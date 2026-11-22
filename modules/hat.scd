// hat - a drum: six square waves through a highpass, closed or open
//
// The 808's hat, as the Mutable Instruments Peaks remembers it: six square
// waves at ratios that never line up, through a highpass at tone, gone
// after decay - short for closed, long for open. trig hits it, or rate on
// its own clock.
//
// With voices it is that many hats, each control a value per voice: a gate
// with voices into trig hits each one on its own.
(
	category: \drums,
	help: "a hat: six square waves at freq through a highpass at tone, gone after decay (short closed, long open); trig hits it, or rate on its own clock; with voices, a hat per voice",
	about: "An 808-style hi-hat: six square waves at clashing ratios through a highpass, short for closed and long for open. Patch a clock or sequencer gate into trig, or let rate play it on its own.",
	params: (
		freq: "the base pitch of the six square waves, in Hz: higher is more metallic",
		rate: "hits it on its own clock, this many times per second; 0 off",
		trig: "each rising gate (above 0.5) hits it - patch a clock or sequencer here",
		decay: "how long it rings, in seconds: short closed, long open",
		tone: "the highpass cutoff, in Hz: higher is thinner and brighter",
		amp: "the output level"
	),
	poly: { |n| {
		var freq = \freq.kr(205 ! n, spec: [100, 800, \exp]);
		var rate = \rate.kr(0 ! n, spec: [0, 20, 4]);
		var gate = \trig.kr(0 ! n) > 0.5;
		var trig = (Impulse.kr(rate) * (rate > 0)) + (Changed.kr(gate) * gate);
		var env = EnvGen.ar(Env.perc(0.0005, \decay.kr(0.08 ! n, spec: [0.02, 2, \exp]), 1, -5), trig);
		// the six ratios summed per voice
		var sig = [1, 1.4829, 1.8, 2.5463, 2.6341, 3.9024].sum { |r| Pulse.ar(freq * r) };
		(HPF.ar(HPF.ar(sig, \tone.kr(6000 ! n, spec: [1000, 12000, \exp])), 3000) * 2).tanh * env
			* \amp.kr(0.5 ! n, spec: \amp)
	} }
)
