package org.cowboycoders.cyclisimo.content;

import android.database.Cursor;
import android.net.Uri;

import java.util.List;

public interface CyclismoProviderUtils extends MyTracksProviderUtils {
  
  /**
  * Creates a {@link User} from a cursor.
  * 
  * @param cursor the cursor pointing to the user
  */
 public User createUser(Cursor cursor);

 /**
  * Deletes all users (including waypoints and user points).
  */
 public void deleteAllUsers();

 /**
  * Deletes a user.
  * 
  * @param userId the user id
  */
 public void deleteUser(long userId);

 /**
  * Gets all the users. If no user exists, an empty list is returned.
  * <p>
  * Note that the returned users do not have any user points attached.
  */
 public List<User> getAllUsers();


 /**
  * Gets a user by a user id. Returns null if not found.
  * <p>
  * Note that the returned user doesn't have any user points attached.
  * 
  * @param userId the user id.
  */
 public User getUser(long userId);

 /**
  * Gets a user cursor. The caller owns the returned cursor and is responsible
  * for closing it.
  * 
  * @param selection the selection
  * @param selectionArgs the selection arguments
  * @param sortOrder the sort order
  */
 public Cursor getUserCursor(String selection, String[] selectionArgs, String sortOrder);

 /**
  * Inserts a user.
  * <p>
  * Note: This doesn't insert any user points.
  * 
  * @param user the user
  * @return the content provider URI of the inserted user.
  */
 public Uri insertUser(User user);

 /**
  * Updates a user.
  * <p>
  * Note: This doesn't update any user points.
  * 
  * @param user the user
  */
 public void updateUser(User user);
 

 /**
 * Creates a {@link Bike} from a cursor.
 * 
 * @param cursor the cursor pointing to the bike
 */
public Bike createBike(Cursor cursor);

/**
 * Deletes all bikes (including waypoints and bike points).
 */
public void deleteAllBikes();

/**
 * Deletes a bike.
 * 
 * @param bikeId the bike id
 */
public void deleteBike(long bikeId);

/**
 * Gets all the bikes. If no bike exists, an empty list is returned.
 * <p>
 * Note that the returned bikes do not have any bike points attached.
 */
public List<Bike> getAllBikes();

/**
 * Gets the last bike. Returns null if doesn't exist.
 */
public Bike getLastBike();

/**
 * Gets a bike by a bike id. Returns null if not found.
 * <p>
 * Note that the returned bike doesn't have any bike points attached.
 * 
 * @param bikeId the bike id.
 */
public Bike getBike(long bikeId);

/**
 * Gets a bike cursor. The caller owns the returned cursor and is responsible
 * for closing it.
 * 
 * @param selection the selection
 * @param selectionArgs the selection arguments
 * @param sortOrder the sort order
 */
public Cursor getBikeCursor(String selection, String[] selectionArgs, String sortOrder);

/**
 * Inserts a bike.
 * <p>
 * Note: This doesn't insert any bike points.
 * 
 * @param bike the bike
 * @return the content provider URI of the inserted bike.
 */
public Uri insertBike(Bike bike);

/**
 * Updates a bike.
 * <p>
 * Note: This doesn't update any bike points.
 * 
 * @param bike the bike
 */
public void updateBike(Bike bike);



}
