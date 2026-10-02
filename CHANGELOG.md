# Version 2.0.0
### Moved new presets to a base/diff design
"Base" preset (has a filled in star icon to the left of its name) contains a full copy of the saved 
configs similar to how 1.0 did it. 

Presets created after the base preset only contain the changes from "Base" rather than a full copy. This is 
where the power of the new system comes into play. Here are a couple sweet things that you can do now with 
ECS:
1. Updates to the base preset apply to all presets (unless overriden already or afterwards by a given preset).
   An example of this would be say adding resourcepacks or a new shader option to a modpack. Before with 1.0 
   creators would have to delete and recreate all the presets to incorporate these new packs and shaders. Now 
   a creator is able to add the packs/shaders to the modpack, simply configure them as desired, then click 
   the new update button (shaped like a pencil). This will save all those changes to base and be applied to all 
   derivative presets without needing to update every individual preset.
2. If a mod feature or resourcepack are meant to be disabled on a given preset, just switch to that preset and 
    disable them, then click the pencil icon to update that specific preset. Even though base has those applied 
    ECS will remember that this preset has explicitly disabled those.
3. All presets (including base) can now be updated individually without needing that preset to be active. 
    What I mean is that on a fresh game launch, ECS has a snapshot of the current configs and can detect any 
    changes since launch. So even on say a low end performance preset, after a fresh launch, you can add a bunch 
    of heavy PBR resourcepacks to the "active" instance and then update the "Super High End" preset and that will 
    only update that preset with the now activated PBR packs. I hope this makes sense haha.
4. Lastly, this saves a little bit of storage in your modpack. Yay!

# Version 1.0.1
- Fixed mod description
- Backported to 1.21.1 and 1.20.1 :D
- Ported up to 26.1->26.3

# Version 1.0.0
- Initial release :)