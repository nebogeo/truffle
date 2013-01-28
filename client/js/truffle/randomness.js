// t r u f f l e Copyright (C) 2012 FoAM vzw   \_\ __     /\
//                                          /\    /_/    / /  
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

function rndi(from,to) {
    return from+Math.floor(Math.random()*to-from);
}

function rndf() {
    return Math.random();
}

// centred random -1 to 1
function crndf() {
    return (Math.random()-0.5)*2;
}

function rndvec2() {
    return new truffle.vec2(rndf(),rndf(),rndf());
}

function crndvec2() {
    return new truffle.vec2(crndf(),crndf(),crndf());
}

// inside circle
function circ_rndvec2() {
    var v=crndvec2();
    while (v.mag()>1) {
        v=crndvec2();
    }
    return v;
}

// surface of circle
function hollow_circ_rndvec2() {
    var v=circ_rndvec2();
    while (v.mag()!=0) {
        v=circ_rndvec2(); // just in case...
    }
    return v.div(v.mag());
}
