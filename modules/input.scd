// input - a sound card input: a microphone, an instrument
//
// One channel of the server's inputs, chan counting from 0, times gain - to
// play through the rack, or to listen to with follower, pitch, onsets and
// the rest. A chan the server does not have is silence: past its inputs lie
// buses other Ndefs play on.
//
// With voices, an input for each, chan and gain a value per voice; voice
// one starts on input 0, voice two on input 1 and so on (the ninth on 0
// again), so two voices are a stereo pair.
(
	category: \utilities,
	help: "one input of the sound card (chan, counting from 0) times gain: a microphone or an instrument, to play or to listen to; with voices, one input each, chan 0, 1, 2 ... to start with",
	about: "Brings a channel of the sound card into the rack: a microphone or an instrument. Play it through effects, or patch it into follower, pitch or onsets to let what you play drive the patch.",
	params: (
		chan: "which sound card input, counting from 0; one the card does not have is silent",
		gain: "how loud the input comes in: 1 unchanged"
	),
	poly: { |n| {
		var chan = \chan.kr((0 .. n - 1) % 8, spec: [0, 7, \lin, 1]).round;
		var in = n.collect { |i| SoundIn.ar(chan.asArray[i]) }.unbubble;
		in * (chan < NumInputBuses.ir) * \gain.kr(1 ! n, spec: [0, 4, \amp])
	} }
)
