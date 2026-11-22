// clock - a master clock: beats, eighths, sixteenths and bars, as gates
//
// Four channels of gates, high half of each period. Patched into a single
// clock input it is the first one, the beat; pick takes out any other.
//
// With voices it is that many clocks, a bpm and a run each - polyrhythms
// from one module: the beats of every voice first, then the eighths, the
// sixteenths and the bars.
(
	category: \sequencing,
	help: "a clock at bpm: four channels of gates - beat, 8th, 16th, bar; run at 0 stops it; with voices, a clock per voice",
	about: "A master clock at a tempo in bpm: gates on the beat, the eighths, the sixteenths and the bar. Patch it into sequencers, drums and rhythm modules to keep them in step; pick takes out a single channel.",
	params: (
		bpm: "the tempo, in beats per minute",
		run: "1 runs, 0 stops every gate"
	),
	outs: ["beat", "8th", "16th", "bar"],
	poly: { |n| {
		var beat = \bpm.kr(120 ! n, spec: [20, 300]) / 60;
		var run = \run.kr(1 ! n, spec: [0, 1, \lin, 1]);
		[1, 2, 4, 0.25].collect { |m| LFPulse.kr(beat * m, 0, 0.5) * run }.flat
	} }
)
