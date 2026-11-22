// follower - how loud the input is, as a signal
//
// An envelope follower. The level of in, in dB, is scaled from floor..top
// to lo..hi: quieter than floor reads lo, louder than top reads hi. attack
// and release are how fast it rises and how fast it falls. Channel 2 is a
// gate while the level is above thresh; it closes 3 dB lower, so a level
// sitting on thresh does not chatter.
//
// With voices, a follower for each voice's own channel, every control a
// value per voice; out come the levels of every voice, then the gates.
(
	category: \listening,
	help: "an envelope follower: the level of in, floor..top dB scaled to lo..hi; attack, release; channel 2 a gate while above thresh dB; with voices, one per voice",
	about: "An envelope follower: tells how loud a sound is, as a signal from lo to hi, plus a gate while it is above a threshold. Patch it into a filter or a gain to make one sound react to another, or the gate into an envelope.",
	params: (
		in: "the sound to listen to",
		attack: "how fast the reading rises when the sound gets louder, in seconds",
		release: "how fast the reading falls when the sound gets quieter, in seconds",
		thresh: "the level in dB above which the gate opens; it closes 3 dB lower",
		floor: "the level in dB that reads as lo: anything quieter reads lo too",
		top: "the level in dB that reads as hi: anything louder reads hi too",
		lo: "the output when the sound is at floor or quieter",
		hi: "the output when the sound is at top or louder"
	),
	outs: ["level", "gate"],
	poly: { |n| {
		// followed at audio rate: Amplitude.kr on an audio input runs its
		// attack and release about 64 times too fast. Sanitize: a NaN that
		// came in once would leave the follower reading lo for good
		var level = A2K.kr(Amplitude.ar(Sanitize.ar(\in.ar(0 ! n)), \attack.kr(0.01 ! n, spec: [0.001, 1, \exp]),
			\release.kr(0.2 ! n, spec: [0.01, 4, \exp]))).max(1e-5).ampdb;
		var thresh = \thresh.kr(-30 ! n, spec: [-90, 0]);
		var floor = \floor.kr(-60 ! n, spec: [-90, 0]), top = \top.kr(0 ! n, spec: [-60, 12]);
		// top on floor would divide by zero
		top = Select.kr((top - floor).abs < 0.01, [top, floor + 0.01]);
		[
			level.linlin(floor, top, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1])),
			Schmidt.kr(level, thresh - 3, thresh)
		].flat
	} }
)
