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

truffle.drawable=function() 
{
    this.ready_to_draw=true;
    this.hidden=false;
    this.hide_next=false;
    this.draw_me=true;
    this.disable_bg_redraw=false;
    this.id=-1;
    this.width=64;
    this.height=112;
    this.depth=-1;
    this.depth_offset=Math.random(); // attempt to prevent z fighting
    this.colour=null;
    this.expand_bb=0;
    this.alpha=1;

    this.zone=0; // use for partitioning bbox overlaps

    this.pos=new truffle.vec2(0,0);
    this.centre=new truffle.vec2(0,0);
    this.transform = new truffle.mat23();
    this.parent_transform=null;
    this.complex_transform=false;
    this.do_centre_middle_bottom=false;

    this.is_mouseover=false;
    this.mousedown_func=null;
    this.mouseup_func=null;
    this.mouseover_func=null;
    this.mouseout_func=null;
    
    this.enable_mouse(false);

    this.last_bbox=[0,0,0,0];
    this.bbox=[0,0,0,0];
}

truffle.drawable.prototype.set_id=function(s) { this.id=s; }
truffle.drawable.prototype.get_id=function() { return this.id; }

// transforms and general state //////////////////////////////////////

truffle.drawable.prototype.set_pos=function(s) { 
    this.transform.m[4]=s.x; 
    this.transform.m[5]=s.y; 
    this.pos=s; 
    this.draw_me=true; 
}

truffle.drawable.prototype.clear_transform=function() {
    this.transform = new truffle.mat23();
    this.draw_me=true;
}

truffle.drawable.prototype.translate=function(s) { 
    this.transform.translate(s.x,s.y); 
    this.pos=this.pos.add(s); // probably not true 
    this.complex_transform=true; 
    this.draw_me=true; 
}

truffle.drawable.prototype.scale=function(s) { 
    this.transform.scale(s.x,s.y); 
    this.complex_transform=true; 
    this.draw_me=true; 
}

truffle.drawable.prototype.rotate=function(angle) { 
    this.transform.translate(new truffle.vec2(-this.centre.x,-this.centre.y));
    this.transform.rotate(angle); 
    this.transform.translate(this.centre);
    this.complex_transform=true; 
    this.draw_me=true; 
}

truffle.drawable.prototype.get_tx=function() { 
    return this.transform; 
}

truffle.drawable.prototype.set_depth=function(s) {
    this.depth=s;
}

truffle.drawable.prototype.get_depth=function() {
    return this.depth+this.depth_offset;
}

truffle.drawable.prototype.get_colour=function() {
    return this.colour;
}

truffle.drawable.prototype.set_colour=function(s) {
    this.colour=s;
}

truffle.drawable.prototype.transformed_pos=function() {
    return this.transform.transform_point(0,0);
}

truffle.drawable.prototype.hide=function(s) {
    this.draw_me=true;

//    this.hidden=s;

    if (s==true && !this.hidden) {
        // defer so we can repaint the bg
        this.hide_next=true;
    } else {
        this.hidden=s;
    }
}

// bounding boxes ///////////////////////////////////////////////////

truffle.drawable.prototype.set_size=function(x,y) {
    this.width=x;
    this.height=y;
    if (this.do_centre_middle_bottom) {
        this.centre.x=this.width/2;
        this.centre.y=this.height;            
    }
    else {
        this.centre.x=this.width/2;
        this.centre.y=this.height/2;
    }
}

truffle.drawable.prototype.centre_middle_bottom=function(s) {
    this.do_centre_middle_bottom=s;
}

truffle.drawable.prototype.get_last_bbox=function() {
    return this.last_bbox;
}

truffle.drawable.prototype.get_bbox=function() {
    return this.bbox;
}

truffle.drawable.prototype.get_delta_bbox=function() {
    var l=this.bbox[0];
    var t=this.bbox[1];
    var r=this.bbox[2];
    var b=this.bbox[3];

/*    if (this.last_bbox[0]<l) l=this.last_bbox[0];
    if (this.last_bbox[1]<t) t=this.last_bbox[1];
    if (this.last_bbox[2]>r) r=this.last_bbox[2];
    if (this.last_bbox[3]>b) b=this.last_bbox[3];*/

    if (l>this.last_bbox[0]) l=this.last_bbox[0];
    if (t>this.last_bbox[1]) t=this.last_bbox[1];
    if (r<this.last_bbox[2]) r=this.last_bbox[2];
    if (b<this.last_bbox[3]) b=this.last_bbox[3];

    return [l,t,r,b];
}

truffle.drawable.prototype.recalc_bbox=function() {
    this.last_bbox=[this.bbox[0],this.bbox[1],this.bbox[2],this.bbox[3]];
    var l=this.pos.x-this.centre.x;
    var t=this.pos.y-this.centre.y;
    if (this.do_centre_middle_bottom) t=this.pos.y-this.height;
    if (this.expand_bb>0) { // cater for rotate 
        var m=Math.max(this.width,this.height);
        var h=(m/2)+this.expand_bb;
        this.bbox=[~~(0.5+l-h),~~(0.5+t-h),
                   ~~(0.5+l+m+h),~~(0.5+t+m+h)]; 
    }
    else {
        this.bbox=[~~(0.5+l),~~(0.5+t),
                   ~~(0.5+l+this.width),
                   ~~(0.5+t+this.height)]; 
    }
}

truffle.drawable.prototype.intersect=function(ob) {
    var tb=this.get_bbox();
    return !(ob[0] > tb[2] || ob[2] < tb[0] ||
             ob[1] > tb[3] || ob[3] < tb[1]);
}

truffle.drawable.prototype.draw = function(ctx) {
    if (this.hide_next) {
        this.hidden=true;
        this.hide_next=false;
    }
}

// mouse events //////////////////////////////////////////////////

truffle.drawable.prototype.enable_mouse=function(s) {
    this.mouse_enabled=s;
}
 
truffle.drawable.prototype.is_mouse_enabled=function() {
    return this.mouse_enabled;
}

truffle.drawable.prototype.update_input=function(cs) {}

truffle.drawable.prototype.mouse_down=function(f) {
    this.enable_mouse(true);
    this.mousedown_func=f;
}

truffle.drawable.prototype.mouse_up=function(f) {
    this.enable_mouse(true);
    this.mouseup_func=f;
}

truffle.drawable.prototype.mouse_over=function(f) {
    this.enable_mouse(true);
    this.mouseover_func=f;
}

truffle.drawable.prototype.mouse_out=function(f) {
    this.enable_mouse(true);
    this.mouseout_func=f;
}

truffle.drawable.prototype.update_mouse=function(canvas_state) {
    // assume check for mouseenabled is done already
    var x=canvas_state.mouse_x+this.centre.x;
    var y=canvas_state.mouse_y+this.centre.y;

    // todo - correct for transform
    if (x>this.pos.x && x<this.pos.x+this.width &&
        y>this.pos.y && y<this.pos.y+this.height) {
        if (!this.is_mouse_over) {
            if (this.mouseover_func!=null) this.mouseover_func();
            this.is_mouse_over=true;
            return true;
        }

        if (canvas_state.mouse_changed) {
            if (canvas_state.mouse_down) {
                if (this.mousedown_func!=null) this.mousedown_func();
                return true;
            }
            else {
                if (this.mouseup_func!=null) this.mouseup_func();
                return true;
            }
        }
    }
    else {
        if (this.is_mouse_over) {
            if (this.mouseout_func!=null) this.mouseout_func();
            this.is_mouse_over=false;
            return true;
        }
    }

    return false;
}


