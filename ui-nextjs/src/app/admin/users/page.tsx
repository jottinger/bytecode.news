"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Role } from "@/lib/api/types";
import { resetUserPassword, updateUserRole } from "@/lib/api/admin";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

const roleSchema = z.object({
  username: z.string().min(1, "Username is required"),
  newRole: z.nativeEnum(Role, { error: "Role is required" }),
});

type RoleValues = z.infer<typeof roleSchema>;

const resetSchema = z.object({
  username: z.string().min(1, "Username is required"),
});

type ResetValues = z.infer<typeof resetSchema>;

export default function AdminUsersPage() {
  const [resetOpen, setResetOpen] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(
    null
  );
  const [resetUsername, setResetUsername] = useState<string | null>(null);

  const roleForm = useForm<RoleValues>({
    resolver: zodResolver(roleSchema),
    defaultValues: { username: "", newRole: undefined },
  });

  const resetForm = useForm<ResetValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { username: "" },
  });

  async function onRoleSubmit(values: RoleValues) {
    try {
      await updateUserRole(values.username, { newRole: values.newRole });
      toast.success(
        `Role for "${values.username}" changed to ${values.newRole}.`
      );
      roleForm.reset();
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.detail
          : "Failed to update user role.";
      toast.error(message);
    }
  }

  async function handleResetPassword() {
    const username = resetForm.getValues("username");
    if (!username) return;
    setResetting(true);
    try {
      const result = await resetUserPassword(username);
      setTemporaryPassword(result.temporaryPassword);
      setResetUsername(result.username);
      toast.success(`Password reset for "${result.username}".`);
      resetForm.reset();
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.detail
          : "Failed to reset password.";
      toast.error(message);
    } finally {
      setResetting(false);
      setResetOpen(false);
    }
  }

  return (
    <div className="space-y-8">
      <h2 className="text-2xl font-bold">Users</h2>

      <Card>
        <CardHeader>
          <CardTitle>Change User Role</CardTitle>
          <CardDescription>
            Update a user&apos;s role by username.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...roleForm}>
            <form
              onSubmit={roleForm.handleSubmit(onRoleSubmit)}
              className="space-y-4"
            >
              <FormField
                control={roleForm.control}
                name="username"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Username</FormLabel>
                    <FormControl>
                      <Input placeholder="Username" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={roleForm.control}
                name="newRole"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Role</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="Select a role" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value={Role.GUEST}>Guest</SelectItem>
                        <SelectItem value={Role.USER}>User</SelectItem>
                        <SelectItem value={Role.ADMIN}>Admin</SelectItem>
                        <SelectItem value={Role.SUPER_ADMIN}>
                          Super Admin
                        </SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button
                type="submit"
                disabled={roleForm.formState.isSubmitting}
              >
                {roleForm.formState.isSubmitting
                  ? "Updating..."
                  : "Update role"}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Reset User Password</CardTitle>
          <CardDescription>
            Generate a temporary password for a user.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Form {...resetForm}>
            <form
              onSubmit={(e) => e.preventDefault()}
              className="flex items-end gap-3"
            >
              <FormField
                control={resetForm.control}
                name="username"
                render={({ field }) => (
                  <FormItem className="flex-1">
                    <FormLabel>Username</FormLabel>
                    <FormControl>
                      <Input placeholder="Username" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Dialog open={resetOpen} onOpenChange={setResetOpen}>
                <DialogTrigger asChild>
                  <Button variant="destructive">Reset password</Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Reset password?</DialogTitle>
                    <DialogDescription>
                      This will generate a new temporary password for the user.
                      Their current password will stop working.
                    </DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button
                      variant="ghost"
                      onClick={() => setResetOpen(false)}
                    >
                      Cancel
                    </Button>
                    <Button
                      variant="destructive"
                      onClick={handleResetPassword}
                      disabled={resetting}
                    >
                      {resetting ? "Resetting..." : "Reset password"}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </form>
          </Form>

          {temporaryPassword && resetUsername && (
            <Card className="border-primary/50 bg-primary/5">
              <CardContent className="py-4">
                <p className="text-sm font-medium">
                  Temporary password for{" "}
                  <span className="font-bold">@{resetUsername}</span>:
                </p>
                <p className="mt-1 font-mono text-lg">{temporaryPassword}</p>
                <p className="text-muted-foreground mt-2 text-xs">
                  Share this with the user securely. They should change it after
                  logging in.
                </p>
              </CardContent>
            </Card>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
