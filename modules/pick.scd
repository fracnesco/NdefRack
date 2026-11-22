// pick - one channel out of a wide cable
//
// A cable carries every channel of its Ndef, and a single input takes the
// first. This takes any other: turing's gate is chan 1, clock's sixteenths
// chan 2.
//
// With voices, each voice picks a channel of its own out of the same eight,
// chan a value per voice: voice one starts on chan 0, voice two on chan 1
// and so on (the ninth on 0 again).
(
	category: \utilities,
	help: "picks one channel (chan, counting from 0) out of up to eight coming in; with voices, a channel per voice out of the same eight, 0, 1, 2 ... to start with",
	about: "Takes one channel out of a wide cable. A cable into a plain input carries only its first channel; this reaches the others - a sequencer's gate, a clock's sixteenths.",
	params: (
		in: "the wide cable to pick from, up to eight channels",
		chan: "which channel comes out, counting from 0"
	),
	poly: { |n| { Select.ar(\chan.kr((0 .. n - 1) % 8, spec: [0, 7, \lin, 1]).round, \in.ar(0 ! 8)) } }
)
