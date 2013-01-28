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

truffle.vec2=function(x,y) {
    this.x=x;
    this.y=y;
}

truffle.vec2.prototype.add=function(other) {
	return new truffle.vec2(this.x+other.x,this.y+other.y);
}

truffle.vec2.prototype.sub=function(other) {
	return new truffle.vec2(this.x-other.x,this.y-other.y);
}

truffle.vec2.prototype.div=function(v) {
	return new truffle.vec2(this.x/v,this.y/v);
}

truffle.vec2.prototype.mul=function(v) {
	return new truffle.vec2(this.x*v,this.y*v);
}

truffle.vec2.prototype.mag=function() {
	return Math.sqrt(this.x*this.x+this.y*this.y);
}

truffle.vec2.prototype.normalise=function() {
    return this.div(this.mag());
}

truffle.vec2.prototype.lerp=function(other,t) {
	return new truffle.vec2(this.x*(1-t) + other.x*t,
					        this.y*(1-t) + other.y*t);
}

truffle.vec2.prototype.eq=function(other) {
	return this.x==other.x && this.y==other.y;
}

truffle.vec2.prototype.as_str=function() {
    return str(this.x)+", "+str(this.y);
}

