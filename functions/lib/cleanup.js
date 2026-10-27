'use strict';

/**
 * Expired story cleanup (task P5-04).
 *
 * Stories are hidden by a query filter (`expiresAt > now`), never deleted — so until this
 * runs, every expired story and every view marker stays in the database forever. With the
 * client on Spark there was no way to schedule it. That is the whole reason this task was
 * blocked: not missing code, missing a trigger.
 *
 * Deletes are bounded per run so a large backlog cannot push one invocation past its
 * timeout; the next schedule tick picks up where this one stopped.
 */

const STORY_LIMIT = 300;
const VIEW_LIMIT = 200;

/**
 * @param {FirebaseFirestore.Firestore} db
 * @param {number} now epoch millis
 * @param {number} limit max stories deleted in this run
 * @returns {{deletedStories:number, deletedViews:number, remaining:boolean}}
 */
async function cleanupExpiredStories(db, now = Date.now(), limit = STORY_LIMIT) {
  const expired = await db
    .collection('stories')
    .where('expiresAt', '<=', now)
    .limit(limit)
    .get();

  if (expired.empty) {
    return { deletedStories: 0, deletedViews: 0, remaining: false };
  }

  let deletedViews = 0;
  const storyIds = expired.docs.map((d) => d.id);

  // Delete each story's view markers first: once the story is gone, there is no way to
  // find its views again, so doing it in the other order would orphan them permanently.
  for (const storyId of storyIds) {
    // eslint-disable-next-line no-await-in-loop
    const views = await db
      .collection('storyViews')
      .where('storyId', '==', storyId)
      .limit(VIEW_LIMIT)
      .get();
    if (views.empty) continue;
    const batch = db.batch();
    views.docs.forEach((v) => batch.delete(v.ref));
    // eslint-disable-next-line no-await-in-loop
    await batch.commit();
    deletedViews += views.size;
  }

  const storyBatch = db.batch();
  expired.docs.forEach((d) => storyBatch.delete(d.ref));
  await storyBatch.commit();

  return {
    deletedStories: expired.size,
    deletedViews,
    remaining: expired.size === limit,
  };
}

module.exports = { cleanupExpiredStories, STORY_LIMIT };
