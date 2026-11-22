// snare - a drum: two tuned bodies and a burst of noise
//
// After the drum modes of the Mutable Instruments Peaks. The body is two
// sines, at freq and a fifth and a bit above, dropping a little on the hit
// and gone after decay; the snare is white noise through a highpass, gone
// after snap. tone mixes them, body to noise. trig hits it, or rate on its
// own clock.
//
// With voices it is that many snares, each control a value per voice and
// each with a noise of its own.
(
	category: \drums,
	help: "a snare: two sines at freq (the body, gone after decay) and a highpassed noise burst (gone after snap), mixed by tone; trig hits it, or rate on its own clock; with voices, a snare per voice",
	about: "A synthetic snare after Mutable Instruments Peaks: two tuned sines for the body and a burst of highpassed noise for the snares, mixed by tone. Hit it with trig or let rate run its own clock.",
	params: (
		freq: "the body's pitch, in Hz",
		rate: "its own clock, in hits per second; 0 off, hit only by trig",
		trig: "hits the drum on its rising edge, on above 0.5 - press it, or patch a clock or sequencer gate",
		decay: "how long the body rings, in seconds",
		snap: "how long the noise burst lasts, in seconds",
		hpf: "the noise's highpass cutoff, in Hz: up is thinner and brighter",
		tone: "-1 body only, 1 noise only, 0 an even mix",
		amp: "the output level"
	),
	poly: { |n| {
		var freq = \freq.kr(180 ! n, spec: [60, 600, \exp]);
		var rate = \rate.kr(0 ! n, spec: [0, 20, 4]);
		var gate = \trig.kr(0 ! n) > 0.5;
		var trig = (Impulse.kr(rate) * (rate > 0)) + (Changed.kr(gate) * gate);
		var drop = EnvGen.ar(Env.perc(0.0005, 0.03, 1, -4), trig);
		var body = EnvGen.ar(Env.perc(0.0005, \decay.kr(0.15 ! n, spec: [0.02, 1, \exp]), 1, -6), trig)
			* [1, 1.62].sum { |r| SinOsc.ar(freq * r * (1 + (drop * 0.5))) } * 0.5;
		var noise = EnvGen.ar(Env.perc(0.0005, \snap.kr(0.2 ! n, spec: [0.02, 1, \exp]), 1, -5), trig)
			* HPF.ar(WhiteNoise.ar(1 ! n), \hpf.kr(1200 ! n, spec: [200, 8000, \exp]));
		XFade2.ar(body, noise, \tone.kr(0 ! n, spec: [-1, 1])).tanh * \amp.kr(0.5 ! n, spec: \amp)
	} }
)
