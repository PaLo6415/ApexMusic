package code.name.monkey.retro.glide.artistimage

import code.name.monkey.retro.model.Artist

class ArtistImage(val artist: Artist){
    override fun equals(other: Any?): Boolean {
        if (other is ArtistImage){
            return other.artist == artist
        }
        return false
    }

    override fun hashCode(): Int {
        return artist.hashCode()
    }
}