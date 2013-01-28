// Naked on Pluto Copyright (C) 2010 Aymeric Mansoux, Marloes de Valk, Dave Griffiths
//                                       
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

function fb_interface(appid,refresh) {   
    if (appid!="") {
	    FB.init({appId: appid, status: true, cookie: true, xfbml: true});
    }

    this.refresh=refresh;
    this.friends=[];
    this.current_friend=-1;
    this.accessToken=false;
    this.uid=false;

    if (appid!="") {
        this.data = {
            me: {},
            people: [],
        }
    } else {
        this.current_friend=0;
        // some test data
        this.data = {
            me: { id:99, first_name:"Bob", gender:"male",
                  last_name:"Bloggs", name:"Bob Bloggs"},
            people: ["FredFoo","JimJobble","BobbyBoobar"]
        }
        refresh("me",this.data.me);
    }

    /////////////////////////////////////////////////////////////////
    // the login stuff

    this.login=function() {
        var fb=this;

        FB.getLoginStatus(function(response) {
            if (response.status=="connected") {
                facebook_status('I am now naked on Pluto. <input type="button" value="Logout" onClick="game.fb.logout();">');
		        fb.uid = response.authResponse.userID;
		        fb.accessToken = response.authResponse.accessToken;
                fb.suck();
	        } else {
		        FB.login(function(response) {
			        if (response.authResponse) {
			            fb.accessToken = response.authResponse.accessToken;
			            facebook_status('I am now naked on Pluto. <input type="button" value="Logout" onClick="game.fb.logout();">');
			            fb.suck();
			        } else {
                        window.location="http://naked-on-pluto.net";
			            //facebook_status(":( the login didn't work! " + '<input type="button" value="Login to facebook" onClick="login();">');
			        }
		        }, {scope:'user_about_me,friends_about_me,user_activities,friends_activities,user_education_history,friends_education_history,user_events,friends_events,user_groups,friends_groups,user_hometown,friends_hometown,user_interests,friends_interests,user_likes,friends_likes,user_location,friends_location,user_notes,friends_notes,friends_website,user_work_history,friends_work_history'});
	        }		
	    });
    }
    

    this.logout=function() {
        FB.logout(function() {
            window.location="http://borrowed-scenery.org/aniziz";
        });
    };
        


    ////////////////////////////////////////////////////////////////
    // get the data from the api

    this.safe_add = function(whence,data) { 
	    if (data!=undefined)  {
            log("adding "+data+" to "+whence);
            this.data[whence].push(data);
        }
    }

    this.suck_friend=function(friend) {
	    var fb=this;
        var name=friend.name;
        fb.safe_add("people",name);
    }

    this.poll = function() {
        if (this.current_friend>-1 && appid!="") {
            if (this.current_friend<this.friends.length) {
                this.suck_friend(this.friends[this.current_friend]);
                this.current_friend=this.current_friend+1;
            } else {
                log("loading done");
                this.current_friend=-1;
            }
        }       

        if (appid=="" && this.current_friend>-1) {
            this.current_friend=-1;
        }
    }

    this.suck_friends=function() {
	    var fb=this;
        FB.api('/me/friends', function(friends) {
            fb.friends=friends.data;
            fb.current_friend=0;
        });
    }

    this.suck_me=function() {
        var fb=this;
        FB.api('/me', function(response) {
            fb.data.me = response;
            fb.refresh("me",response);
            fb.suck_friends();
        });
    }
    
    this.suck = function() {
        log("looking at facebook data...");
        this.suck_me();
    }
}
