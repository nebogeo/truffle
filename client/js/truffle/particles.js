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

// modes:
// "one-shot" - puff of particles, use finished to desroy
// "continuous"
// "reverse-continuous" 

truffle.particles=function(pos, tex, count, mode) {	
    truffle.drawable.call(this);
    this.pos=pos;
    this.mode=mode;
    this.max_age=100;
    this.global_age=0;
    this.particles=[];
    this.delete_me=false; // will be checked by owner entity
    this.disable_bg_redraw=true;

    for (var i=0; i<count; i++) {
        this.particles.push({
            "vel": circ_rndvec2().mul(100), // px per sec
            "pos": new truffle.vec2(0,0),
            "age": rndi(0,this.max_age)
        });
    }

    this.image=null;
    this.draw_image=null;
    this.ready_to_draw=false;
    this.offset_colour=null;
    this.draw_bb=false;
    this.change_bitmap(tex);    
    this.last_pos=new truffle.vec2(this.pos.x,this.pos.y);
    this.set_pos(this.pos);
}

truffle.particles.prototype=inherits_from(truffle.drawable,truffle.particles);

truffle.particles.prototype.set_bitmap=function(b,recalc_bb) {
    this.image=b;
    this.ready_to_draw=true;
    if (recalc_bb==undefined) this.set_size(this.image.width,this.image.height);
    this.draw_me=true;
    this.draw_image=b;
}

truffle.particles.prototype.change_bitmap=function(t) {
    if (this.image==null || 
        t.name!=this.image.src) {
        this.load_from_url(t);
    }
}

truffle.particles.prototype.load_from_url=function(url) {
    var c=this;
    this.image=new Image();
    this.image.onload = function() {
        //log("loaded "+c.image.src);
        c.ready_to_draw=true;
        c.set_size(c.image.width,c.image.height);
        if (c.offset_colour!=null) {
            c.add_tint(c.offset_colour);
        }
        c.draw_image = c.image;
        c.draw_me=true;
    };
    this.image.onerror = function(e) {
        log("could't load "+url);
    }
    this.image.src = url;  
}

truffle.particles.prototype.update=function(time, delta) {
    this.draw_me=true;
    var that=this;

    this.particles.forEach(function(particle) {
        if (that.mode!="one-shot" && 
            particle.age>that.max_age) {
            particle.pos.x=0;
            particle.pos.y=0;
            particle.age=Math.floor(Math.random()*5);
        }
        particle.pos.x+=particle.vel.x*delta;
        particle.pos.y+=particle.vel.y*delta;
        particle.vel.x*=0.95;
        particle.vel.y*=0.95;
        particle.age++;
    });
    
    if (this.mode=="one-shot" && this.global_age>this.max_age) {
        this.delete_me=true;
    }

    this.global_age++;
}

truffle.particles.prototype.inner_draw=function(ctx,x,y) {
    var that=this;
    //ctx.globalCompositeOperation = "lighter";
    this.particles.forEach(function(particle) {
        if (particle.age<that.max_age) {
            ctx.globalAlpha=1-particle.age/(that.max_age+1); // +1 to sort glitch
            ctx.drawImage(that.draw_image,
                          ~~(0.5+particle.pos.x+x),
                          ~~(0.5+particle.pos.y+y));
        }
    });
}


truffle.particles.prototype.draw=function(ctx) {
    if (!this.ready_to_draw) return;
    truffle.drawable.prototype.draw.call(this,ctx);
    if (this.hidden) return;

    // two render paths
    if (this.complex_transform || this.parent_transform) {
        ctx.save();
        ctx.transform(this.transform.m[0],
                      this.transform.m[1],
                      this.transform.m[2],
                      this.transform.m[3],
                      this.transform.m[4],
                      this.transform.m[5]);
        
        if (this.parent_transform!=null) {
            ctx.transform(this.parent_transform.m[0],
                          this.parent_transform.m[1],
                          this.parent_transform.m[2],
                          this.parent_transform.m[3],
                          this.parent_transform.m[4],
                          this.parent_transform.m[5]);
        }
        
        this.inner_draw(ctx,-this.centre.x,-this.centre.y);
        ctx.restore();
    }
    else // simple render path
    {
        this.inner_draw(ctx,this.pos.x-this.centre.x,
                        this.pos.y-this.centre.y);
    }

    if (this.draw_bb) {
        // draw bbox
        ctx.beginPath();
        ctx.strokeStyle = "#00ffff";
        var bb=this.get_bbox();
        ctx.rect(bb[0], bb[1], bb[2]-bb[0], bb[3]-bb[1]); 
        ctx.stroke();
    }
    
    this.last_pos.x=this.pos.x;
    this.last_pos.y=this.pos.y;

    this.recalc_bbox();
    this.draw_me=false;
}

