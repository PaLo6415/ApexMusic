package code.name.monkey.retro.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import code.name.monkey.retro.R;
import code.name.monkey.retro.model.Song;

public class AutoGeneratedPlaylistBitmap {

    public static Bitmap getBitmap(
            Context context, List<Song> songPlaylist) {
        if (songPlaylist == null || songPlaylist.isEmpty()) return getDefaultBitmap(context);
        if (songPlaylist.size() == 1)
            return getBitmapWithAlbumId(context, songPlaylist.get(0).getAlbumId());
        List<Long> albumID = new ArrayList<>();
        for (Song song : songPlaylist) {
            if (!albumID.contains(song.getAlbumId())) albumID.add(song.getAlbumId());
        }
        List<Bitmap> art = new ArrayList<>();
        for (Long id : albumID) {
            Bitmap bitmap = getBitmapWithAlbumId(context, id);
            if (bitmap != null) art.add(BitmapEditor.getRoundedCornerBitmap(bitmap, 20));
            if (art.size() == 9) break;
        }
        return MergedImageUtils.INSTANCE.joinImages(art);
    }

    private static Bitmap getBitmapWithAlbumId(@NonNull Context context, Long id) {
        try {
            return Glide.with(context)
                    .asBitmap()
                    .load(MusicUtil.getMediaStoreAlbumCoverUri(id))
                    .submit(200, 200)
                    .get();
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap getDefaultBitmap(@NonNull Context context) {
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_album_art);
    }
}
