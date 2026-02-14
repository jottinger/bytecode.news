"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { editComment } from "@/lib/api/comments";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from "@/components/ui/form";

const editSchema = z.object({
  markdownSource: z.string().min(1, "Comment cannot be empty"),
});

type EditValues = z.infer<typeof editSchema>;

interface CommentEditFormProps {
  commentId: string;
  initialContent: string;
  onSuccess: () => void;
  onCancel: () => void;
}

export function CommentEditForm({
  commentId,
  initialContent,
  onSuccess,
  onCancel,
}: CommentEditFormProps) {
  const form = useForm<EditValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { markdownSource: initialContent },
  });

  async function onSubmit(values: EditValues) {
    try {
      await editComment(commentId, {
        markdownSource: values.markdownSource,
      });
      onSuccess();
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.detail
          : "Failed to edit comment.";
      toast.error(message);
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
        <FormField
          control={form.control}
          name="markdownSource"
          render={({ field }) => (
            <FormItem>
              <FormControl>
                <Textarea rows={3} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className="flex gap-2">
          <Button
            type="submit"
            size="sm"
            disabled={form.formState.isSubmitting}
          >
            {form.formState.isSubmitting ? "Saving..." : "Save"}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Form>
  );
}
