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

truffle.world=function() {
    this.clear();
}

truffle.world.prototype.clear=function() {
    this.scene=[];
    this.sprites=[];
    this.current_depth=1000;
    this.current_id=0;
    this.time=0;
    this.delta=0;
    this.do_render=true;
    this.frame=0;

    this.canvas_state=new truffle.canvas_state();
    this.current_tile_pos=new truffle.vec2(0,0); // perhaps
    this.screen_scale=new truffle.vec2(1,1);
    this.screen_offset=new truffle.vec2(0,0);
    this.debug_text="hello world\n";

    // iso rotation values
    this.theta = 66*Math.PI/180;
    this.alpha = 59*Math.PI/180;

    this.cos_theta = Math.cos(this.theta);
    this.sin_theta = Math.sin(this.theta);
    this.cos_alpha = Math.cos(this.alpha);
    this.sin_alpha = Math.sin(this.alpha);
    this.scale = new truffle.vec3(103,84,80);

    var canvas=document.getElementById('canvas')
    var ctx=canvas.getContext('2d');
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0,0,canvas.width,canvas.height);
}

/*
truffle.world.prototype.screenspace_transform=function(pos) {
    var ox=pos.x*this.scale.x;
    var oy=pos.y*this.scale.y;
    var oz=pos.z*this.scale.z;
    
    var zp=oz;
    var xp=ox*this.cos_alpha+oy*this.sin_alpha;
    var yp=oy*this.cos_alpha-ox*this.sin_alpha;
    
    var r= new truffle.vec3(
        xp,
        yp*this.cos_theta+zp*this.sin_theta,
        zp*this.cos_theta-yp*this.sin_theta
    );
    return r; 
}

truffle.world.prototype.screen_transform=function(pos) {
    var r=this.screenspace_transform(pos);
    r.x+=this.screen_offset.x;
    r.y+=this.screen_offset.y;
    return r;
}

truffle.world.prototype.inverse_screen_transform=function(pos) {
    var ox=pos.x-this.screen_offset.x;
    var oy=pos.y-this.screen_offset.y;
    var xp=ox/this.cos_alpha+oy/this.sin_alpha;
    var yp=oy/this.cos_alpha-ox/this.sin_alpha;
    var r= new truffle.vec2(xp,yp/this.cos_theta);
    r.x/=this.scale.x;
    r.y/=this.scale.y;
    return r;
}
*/

truffle.world.prototype.screenspace_transform=function(pos) {
    return pos; 
}

truffle.world.prototype.screen_transform=function(pos) {
    return pos;
}

truffle.world.prototype.inverse_screen_transform=function(pos) {
    return pos;
}

/////////////////////////////////////////////////////////

truffle.world.prototype.in_screen_coords=function(x,y) {
    return new truffle.vec2(x-this.canvas_state.world_x,
                            y-this.canvas_state.world_y);
}

truffle.world.prototype.add=function(e) {
    this.scene.push(e);
}

truffle.world.prototype.remove=function(e) {
    e.destroy(this);
    remove(this.scene,e);
}

truffle.world.prototype.get=function(type, pos) {
    var ret=null;
    this.scene.forEach(function(e) {
        if (pos.x==e.logical_pos.x &&
            pos.y==e.logical_pos.y &&
            e.type==type) {
            ret=e;
        }
    });
    return ret;
}

truffle.world.prototype.get_by_game_type=function(type, pos) {
    var ret=null;
    this.scene.forEach(function(e) {
        if (pos.x==e.logical_pos.x &&
            pos.y==e.logical_pos.y && 
            e.game_type==type) {
            ret=e;
        }
    });
    return ret;
}

truffle.world.prototype.get_other=function(me, type, pos) {
    this.scene.forEach(function (e) {
        if (pos.x==e.logical_pos.x &&
            pos.y==e.logical_pos.y &&
            typeof e==type &&
            e.id!=me.id) {
            return e;
        }
    });
    return null;
}

truffle.world.prototype.set_current_tile_pos=function(s) {
    this.current_tile_pos=s;
}

// override for things on top
truffle.world.prototype.pre_sort_scene=function(depth) {
    return depth;
}

// go through each entity in depth order, calling sort on them
// to arrange child sprites in the correct order
truffle.world.prototype.sort_scene=function() {        
    this.scene.sort(function(a, b) {                       
        if (a.get_depth()<b.get_depth()) return -1;
        else return 1;
    });
    var i=this.pre_sort_scene(0);
    this.scene.forEach(function (e) {
        i=e.on_sort_scene(this,i);
    });    
}

truffle.world.prototype.add_sprite=function(s) {
    s.set_depth(this.current_depth++); // hack to emulate flash draw order
    s.set_id(this.current_id++);
    s.recalc_bbox();
    s.recalc_bbox(); // do twice to init last_bbox
    this.sprites.push(s);
}

truffle.world.prototype.remove_sprite=function(s){
    remove(this.sprites,s);
}

truffle.world.prototype.set_child_index=function(sprite,depth) {
    sprite.set_depth(depth);
}

truffle.world.prototype.mouse_down=function(f) {
}

truffle.world.prototype.mouse_up=function(f) {
}

truffle.world.prototype.mouse_move=function(f) {
}



truffle.world.prototype.debug=function(txt) {
    this.debug_text+=txt+"\n";

    var canvas=document.getElementById('canvas')
    var ctx=canvas.getContext('2d');

    ctx.fillText(this.debug_text, 10, 10, 100);
}

// update all the entities that need it, at the correct frequency
truffle.world.prototype.update_entities=function() {
    var that=this;
    this.scene.forEach(function(e) {
//        if (e.tile_pos!=null)
//        {
            // the current tile pos is surrounded by 8 other
            // visible ones, so see if we are in one of those
 //           var diff=e.tile_pos.sub(that.current_tile_pos);
 //           e.hide(Math.abs(diff.x)>1 || Math.abs(diff.y)>1);
//        }

        if (e.needs_update && !e.hidden &&
            (e.update_freq==0 ||
             (that.frame % e.update_freq)==0))
        {   
            e.update(that.time,that.delta,that);
        }
    });
}

// sort the sprite list based on previous scene sorting order
truffle.world.prototype.sort_sprites=function() {
    this.sprites.sort(function(a, b) {                       
        if (a.get_depth()>b.get_depth()) return -1;
        else return 1;
    });
}

// either adds sprites to the draw list, or collects bounding boxes
// required for sprites that overlap with multiple changing objects
// so they only get redrawn once
truffle.world.prototype.add_to_draw_list=function(spr,bbox,draw_list) {
    draw_list.forEach(function(d) {
        if (spr.id===d.spr.id) {
            d.bbox.push(bbox);
            return draw_list;
        }
    });

    draw_list.push({spr:spr,bbox:[bbox]});
    return draw_list;
}

// adds all sprites overlapping the bounding box to the drawlist 
// in depth order, including the sprite owning the bbox if specified
truffle.world.prototype.bbox_to_draw_list=function(draw_list,bbox,sprite) {
    var that=this;
    // look through the other sprites
    that.sprites.forEach(function(other) {
        // if the other is visible
        // and we intersect it
        if (!other.hidden &&
            other.ready_to_draw &&
            other!=sprite &&
            other.intersect(bbox)) {
            // redraw other
            draw_list=that.add_to_draw_list(other,bbox,draw_list);
        }
        
        // add other now - attempt to maintain the order
        if (other==sprite) {
            draw_list=that.add_to_draw_list(other,bbox,draw_list);
        }
    });
    return draw_list;
}

// creates a list of sprites that have changed, or overlapping those
// that have changed, in depth order for redrawing
truffle.world.prototype.build_draw_list=function() {
    var draw_list=[];
    var that=this;
    // do check for overlapping sprites
    this.sprites.forEach(function(sprite) {
        // if this sprite needs redrawing
        if (sprite.zone==0 &&
            !sprite.hidden &&
            sprite.ready_to_draw &&
            sprite.draw_me) {
            sprite.recalc_bbox();
            var bbox=sprite.get_delta_bbox();
            draw_list=that.bbox_to_draw_list(draw_list,bbox,sprite);
        }
    });

    // do the edges of the screen
    var l=-(this.canvas_state.world_x+this.canvas_state.world_offset_x);
    var t=-(this.canvas_state.world_y+this.canvas_state.world_offset_y);        
    var r=l+this.canvas_state.canvas.width;
    var b=t+this.canvas_state.canvas.height;

    if (this.canvas_state.refresh_left>0) {
        draw_list=this.bbox_to_draw_list
        (draw_list,[l,t,l+(this.canvas_state.refresh_left+5),b]);
    }

    if (this.canvas_state.refresh_top>0) {
        draw_list=this.bbox_to_draw_list
        (draw_list,[l,t,r,t+(this.canvas_state.refresh_top+5)]);
    }

    if (this.canvas_state.refresh_right>0) {
        draw_list=this.bbox_to_draw_list
        (draw_list,[r-(this.canvas_state.refresh_right+5),t,r,b]);
    }

    if (this.canvas_state.refresh_bottom>0) {
        draw_list=this.bbox_to_draw_list
        (draw_list,[l,b-(this.canvas_state.refresh_bottom+5),r,b]);
    }

    return draw_list;
}

// draw the draw list, using the clipping bounding boxes 
truffle.world.prototype.draw_scene=function(draw_list) {
    var that=this;
    this.canvas_state.begin_scene();
    
    draw_list.forEach(function(d) {
        that.canvas_state.clear_rects(d.bbox);
    });

    draw_list.forEach(function(d) {
        that.canvas_state.set_clip(d.bbox);
        d.spr.draw(that.canvas_state.ctx);
        that.canvas_state.unclip();    
    });

//    this.canvas_state.stats(draw_list.length/this.sprites.length);
    this.canvas_state.end_scene(this.delta);
}

// look for entities that have set delete_me and remove them from
// the scene
truffle.world.prototype.remove_deleted_entities=function() {
    // recreate the array - splice might be faster, but still
    // requires new arrays etc...
    var new_scene=[];
    for (var i=0; i<this.scene.length; i++) {
        if (!this.scene[i].delete_me) {
            new_scene.push(this.scene[i]);
        }
        else {
            this.scene[i].destroy(this);
        }
    }
    this.scene=new_scene;
}

// top level update
truffle.world.prototype.update=function(time,delta) {
    var that=this;
    this.sort_scene();
    this.time=time;
    this.delta=delta;
    this.remove_deleted_entities();
    this.update_entities();
    this.sort_sprites();
    if (this.do_render) this.draw_scene(this.build_draw_list());    
    this.update_input();
    this.frame++;
}

truffle.world.prototype.clear_screen=function() {
    this.canvas_state.clear_screen();
}

truffle.world.prototype.redraw=function() {
    var that=this;

    this.canvas_state.clear_screen();

    // sort the sprites
    this.sprites.sort(function(a, b) {                       
        if (a.get_depth()>b.get_depth()) return -1;
        else return 1;
    });

    this.canvas_state.begin_scene();

    this.sprites.forEach(function(s) {
        s.draw_me=true;
        if (!s.hidden) {
            s.draw(that.canvas_state.ctx);
        }
    });
    
    this.canvas_state.end_scene(this.delta);
}

truffle.world.prototype.move_world_to=function(x,y) {
    this.canvas_state.move_world_to(x,y);
}

truffle.world.prototype.update_input=function() {
    // update input (runs sprite closures)
    var found_sprite=false;
    // reverse order so topmost are checked first
    for (var n=0; n<this.sprites.length; n++) {
        var i=this.sprites.length-n;
        i--;
        if (!found_sprite && this.sprites[i].is_mouse_enabled()) {
            found_sprite=this.sprites[i].update_mouse(this.canvas_state);
        }
    }
    
    this.canvas_state.update();
}

