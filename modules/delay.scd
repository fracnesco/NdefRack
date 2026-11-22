// delay - an echo with a filter in its feedback
//
// The repeats pass through a lowpass at tone, so each one is darker than
// the last. fb past 1 grows instead of dying away, held back by a soft
// clip. Moving time glides the pitch of what is in the line. With voices,
// a line for each, every control a value per voice.
(
	category: \effects,
	help: "a delay up to 2 s: time (glides when moved), fb (past 1 it grows, softly clipped), tone darkens the repeats, mix; with voices, one per voice",
	about: "An echo of up to two seconds, with a lowpass in the loop so each repeat is darker than the last. Feedback past 1 grows into a softly clipped wash; moving time glides the pitch of the echoes like tape.",
	params: (
		in: "the sound to echo",
		time: "the time between echoes, in seconds; moving it glides the pitch",
		tone: "the lowpass in the feedback, in Hz: lower makes each repeat darker",
		fb: "how much of each echo comes back: 0 one echo, near 1 many, past 1 it grows",
		mix: "0 dry, 1 only echoes"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		var time = \time.kr(0.3 ! n, spec: [0.001, 2, \exp]).lag(0.2);
		var back = LPF.ar(LocalIn.ar(n), \tone.kr(5000 ! n, spec: \freq)) * \fb.kr(0.4 ! n, spec: [0, 1.1]);
		var wet = DelayC.ar(in + back.tanh, 2, time);
		LocalOut.ar(LeakDC.ar(wet));
		XFade2.ar(in, wet, \mix.kr(0.35 ! n, spec: [0, 1]) * 2 - 1)
	} }
)
