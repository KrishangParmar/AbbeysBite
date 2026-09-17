// ===========================================================================
// delete-account — Apple-compliant full account deletion
//
// Deletes all storage objects owned by the caller, then their auth user.
// Database rows cascade from auth.users → profiles → everything else.
// Called from the app with the user's own JWT.
// ===========================================================================

import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), { status: 405 });
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const asUser = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: userData, error: userError } = await asUser.auth.getUser();
  if (userError || !userData?.user) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });
  }
  const userId = userData.user.id;

  const service = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // 1. Remove storage objects in every bucket under the user's folder,
  //    paginating — a long-lived account can exceed one 1000-object page.
  for (const bucket of ["avatars", "meal-photos-private", "recipe-images-public"]) {
    try {
      // Deleting re-shuffles pages, so always re-list page 0 until empty.
      for (let round = 0; round < 100; round++) {
        const { data: files } = await service.storage
          .from(bucket)
          .list(userId, { limit: 1000 });
        if (!files || files.length === 0) break;
        await service.storage
          .from(bucket)
          .remove(files.map((f) => `${userId}/${f.name}`));
        if (files.length < 1000) break;
      }
    } catch (e) {
      console.error(`storage cleanup failed for ${bucket}`, e);
      // Continue — auth deletion below still cascades all DB data.
    }
  }

  // 2. Delete the auth user; all rows cascade via FK constraints.
  const { error: deleteError } = await service.auth.admin.deleteUser(userId);
  if (deleteError) {
    console.error("auth deletion failed", deleteError);
    return new Response(JSON.stringify({ error: "deletion_failed" }), { status: 500 });
  }

  return new Response(JSON.stringify({ ok: true }), {
    headers: { "Content-Type": "application/json" },
  });
});
