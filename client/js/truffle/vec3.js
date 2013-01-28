// t r u f f l e Copyright (C) 2010 FoAM vzw   \_\ __     /\
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

truffle.vec3=function(x, y, z) {
	this.x=x;
    this.y=y;
    this.z=z;
}
	
truffle.vec3.prototype.add=function(other) {
	return new truffle.vec3(this.x+other.x,
                            this.y+other.y,
                            this.z+other.z);
}

truffle.vec3.prototype.sub=function(other) {
	return new truffle.vec3(this.x-other.x,
                            this.y-other.y,
                            this.z-other.z);
}

truffle.vec3.prototype.mul=function(v) {
  	return new truffle.vec3(this.x*v,this.y*v,this.z*v) ;
} 
 
truffle.vec3.prototype.div=function(v) {
	return new truffle.vec3(this.x/v,this.y/v,this.z/v);
}

truffle.vec3.prototype.mag=function() {
	return Math.sqrt(this.x*this.x+
                     this.y*this.y+
                     this.z*this.z);
}

truffle.vec3.prototype.normalise=function() {
    return this.div(this.mag());
}

truffle.vec3.prototype.manhattan=function(other) {
    var d=this.sub(other);
    return d.x*d.x+d.y*d.y+d.z*d.z;
}

truffle.vec3.prototype.lerp=function(other,t) {
	return new truffle.vec3(this.x*(1-t) + other.x*t,
					        this.y*(1-t) + other.y*t,
					        this.z*(1-t) + other.z*t);
}

truffle.vec3.prototype.eq=function(other) {
	return this.x==other.x && 
        this.y==other.y && 
        this.z==other.z;
}

truffle.vec3.prototype.as_str=function() {
    return str(this.x)+", "+
        str(this.y)+", "+
        str(this.z);
}

