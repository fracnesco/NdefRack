// kick - a drum: a sine that drops onto its note
//
// After the drum modes of the Mutable Instruments Peaks, and the 808 before
// it. A sine at freq, starting punch octaves above and falling onto the note
// over sweep seconds, dies away over decay; drive pushes it into a soft clip
// for the click and the grit. trig hits it, or rate on its own clock.
//
// With voices it is that many kicks, each control a value per voice: a gate
// with voices into trig hits each one on its own.
(
	category: \drums,
	help: "a kick: a sine at freq falling from punch octaves above over sweep seconds, decay, drive into a soft clip; trig hits it (the button, or a gate patched in), or rate on its own clock; with voices, a kick per voice",
	about: "A kick drum: a sine that starts high and drops onto its note, with a decaying body. punch and sweep shape the thump, drive adds click and grit. Hit it with a gate from a sequencer or clock, or let rate run it on its own.",
	params: (
		freq: "the note the drum falls onto, in Hz: lower is deeper",
		rate: "hits per second on its own clock; 0 off, so only trig hits it",
		trig: "hits the drum when it goes above 0.5 - the button, or a gate from a sequencer or clock",
		sweep: "how long the drop onto the note takes, in seconds: short is a click, long a boomy zap",
		decay: "how long the drum rings, in seconds",
		punch: "how many octaves above the note each hit starts: more is a harder thump, 0 a plain sine",
		drive: "pushes it into a soft clip: more click, grit and loudness",
		amp: "the output level"
	),
	poly: { |n| {
		var freq = \freq.kr(50 ! n, spec: [20, 200, \exp]);
		var rate = \rate.kr(0 ! n, spec: [0, 20, 4]);
		var gate = \trig.kr(0 ! n) > 0.5;
		var trig = (Impulse.kr(rate) * (rate > 0)) + (Changed.kr(gate) * gate);
		var sweep = EnvGen.ar(Env.perc(0.0005, \sweep.kr(0.05 ! n, spec: [0.005, 0.5, \exp]), 1, -4), trig);
		var env = EnvGen.ar(Env.perc(0.0005, \decay.kr(0.4 ! n, spec: [0.05, 3, \exp]), 1, -6), trig);
		var f = freq * (2 ** (sweep * \punch.kr(2 ! n, spec: [0, 5])));
		// the phase starts over at each hit, so a fast roll never clicks
		var sig = SinOsc.ar(0, Phasor.ar(T2A.ar(trig), f * 2pi / SampleRate.ir, 0, 2pi));
		(sig * \drive.kr(1 ! n, spec: [1, 20, \exp])).tanh * env * \amp.kr(0.5 ! n, spec: \amp)
	} }
)
