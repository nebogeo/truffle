truffle
=======

A HTML5 game engine, with a Clojure server. This is the canonical version of the 
code used in the Germination X and Aniziz games. Incomplete and messy in parts, features include:

* An isometric game world model
* Optimised HTML5 canvas rendering - using "dirty rectangles" approach
* Websockets (experimental, needs much more work: eg. encryption)
* MongoDB database for serialisation
* Server designed for running simulations and continuous update of the game world.
* Integration with Ushahidi for using map location data in a game

