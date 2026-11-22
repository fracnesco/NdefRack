// bands - how loud each part of the spectrum is: eight bands, eight signals
//
// Eight bands an octave apart, at 100, 200, 400 ... 12800 Hz - two bandpass
// filters in a row each, so a tone an octave away reads about 13 dB lower -
// each followed by an envelope follower. Each band's level, in dB, is
// scaled from floor..top to lo..hi, the way follower does it. Eight
// channels, lowest band first: into verbos' levels, its partials follow the
// spectrum of what comes in.
//
// With voices, eight bands for each voice's own channel, every control a
// value per voice; out come the lowest band of every voice, then the next.
(
	category: \listening,
	help: "eight bands an octave apart (100 Hz .. 12.8 kHz): each band's level of in, floor..top dB scaled to lo..hi; attack, release - eight channels, lowest first; with voices, eight per voice, band by band",
	about: "Listens to a sound and tells how loud eight parts of its spectrum are, octave bands from 100 Hz to 12.8 kHz, as eight signals, lowest first. Patch it into verbos' levels so its partials follow the sound coming in.",
	params: (
		in: "the sound to listen to",
		attack: "how fast each band's reading rises when its part gets louder, in seconds",
		release: "how fast each band's reading falls when its part gets quieter, in seconds",
		floor: "the level in dB that reads as lo: anything quieter reads lo too",
		top: "the level in dB that reads as hi: anything louder reads hi too",
		lo: "the output for a band at floor or quieter",
		hi: "the output for a band at top or louder"
	),
	outs: ["100", "200", "400", "800", "1.6k", "3.2k", "6.4k", "12.8k"],
	poly: { |n| {
		var freqs = 100 * (2 ** (0..7));
		// followed at audio rate: Amplitude.kr on an audio input runs its
		// attack and release about 64 times too fast. Sanitize: a NaN that
		// came in once would leave every band reading lo for good
		var in = Sanitize.ar(\in.ar(0 ! n));
		var attack = \attack.kr(0.01 ! n, spec: [0.001, 1, \exp]), release = \release.kr(0.2 ! n, spec: [0.01, 4, \exp]);
		// one entry per band, each a channel per voice
		var levels = freqs.collect { |f| A2K.kr(Amplitude.ar(BPF.ar(BPF.ar(in, f, 0.7), f, 0.7), attack, release)) };
		var floor = \floor.kr(-60 ! n, spec: [-90, 0]), top = \top.kr(0 ! n, spec: [-60, 12]);
		var lo, hi;
		// top on floor would divide by zero
		top = Select.kr((top - floor).abs < 0.01, [top, floor + 0.01]);
		lo = \lo.kr(0 ! n, spec: [-1, 1]); hi = \hi.kr(1 ! n, spec: [-1, 1]);
		levels.collect { |l| l.max(1e-5).ampdb.linlin(floor, top, lo, hi) }.flat
	} }
)
